#include "content/dlc_extract.h"

#include <algorithm>
#include <fstream>
#include <memory>
#include <queue>
#include <set>
#include <span>
#include <stdexcept>
#include <vector>
#include <rex/filesystem/devices/stfs_container_device.h>
#include <rex/filesystem/file.h>
#include <rex/system/xam/content_manager.h>

namespace pinyon_shift::content {
using rex::X_RESULT;
using rex::X_STATUS;

namespace {
constexpr uint32_t kForzaHorizon = 0x4D5309C9;
constexpr uint64_t kSaveLimit = 4ull * 1024 * 1024 * 1024;
constexpr size_t kMaxSaveEntries = 8192;

void ExtractFiles(rex::filesystem::StfsContainerDevice& device,
                  const std::filesystem::path& destination, uint64_t byte_limit,
                  size_t entry_limit, const std::function<bool()>& cancelled) {
  auto* root = device.ResolvePath("");
  if (!root) throw std::runtime_error("Missing package root.");
  std::queue<rex::filesystem::Entry*> entries;
  std::set<std::filesystem::path> paths;
  entries.push(root);
  std::vector<uint8_t> buffer(4 * 1024 * 1024);
  size_t visited = 0;
  uint64_t expanded = 0;
  while (!entries.empty()) {
    if (cancelled && cancelled()) throw std::runtime_error("Content operation cancelled.");
    if (++visited > entry_limit) throw std::runtime_error("Too many package entries.");
    auto* entry = entries.front();
    entries.pop();
    for (const auto& child : entry->children()) entries.push(child.get());
    if (entry == root) continue;
    const auto& name = entry->name();
    if (name.empty() || name == "." || name == ".." || name.back() == '.' ||
        name.back() == ' ' || name.find_first_of("\\/:<>\"|?*") != std::string::npos ||
        std::any_of(name.begin(), name.end(), [](unsigned char c) { return c < 32; })) {
      throw std::runtime_error("Invalid package member name.");
    }
    const auto relative =
        std::filesystem::path(rex::string::utf8_fix_path_separators(entry->path()));
    for (const auto& component : relative) {
      if (component == ".." || component == ".") throw std::runtime_error("Invalid package path.");
    }
    if (relative.is_absolute() || relative.has_root_path() || !paths.insert(relative).second) {
      throw std::runtime_error("Invalid or duplicate package path.");
    }
    const auto output_path = destination / relative;
    if (entry->attributes() & rex::filesystem::kFileAttributeDirectory) {
      std::filesystem::create_directories(output_path);
      continue;
    }
    expanded += entry->size();
    if (expanded > byte_limit) throw std::runtime_error("Package exceeds the extraction limit.");
    std::filesystem::create_directories(output_path.parent_path());
    rex::filesystem::File* raw = nullptr;
    if (entry->Open(rex::filesystem::FileAccess::kFileReadData, &raw) != X_STATUS_SUCCESS) {
      throw std::runtime_error("Could not open package member.");
    }
    std::unique_ptr<rex::filesystem::File, void (*)(rex::filesystem::File*)> input(
        raw, [](rex::filesystem::File* file) { file->Destroy(); });
    std::ofstream output(output_path, std::ios::binary);
    for (size_t offset = 0; offset < entry->size();) {
      if (cancelled && cancelled()) throw std::runtime_error("Content operation cancelled.");
      const auto size = std::min(buffer.size(), entry->size() - offset);
      size_t read = 0;
      if (input->ReadSync(std::span<uint8_t>(buffer.data(), size), offset, &read) !=
              X_STATUS_SUCCESS || read != size) {
        throw std::runtime_error("Incomplete package member.");
      }
      output.write(reinterpret_cast<const char*>(buffer.data()), read);
      if (!output) throw std::runtime_error("Could not write package member.");
      offset += read;
    }
    output.close();
    if (!output) throw std::runtime_error("Could not finish package member.");
  }
}
}  // namespace

// Android counterpart of tools/fh1_archive_extract.cpp --dlc-files.
// The caller verifies the complete package against the supported catalog first.
void ExtractVerifiedDlc(const std::filesystem::path& package,
                        const std::filesystem::path& destination_root,
                        const std::string& package_id,
                        std::function<bool()> cancelled) {
  auto destination = std::filesystem::absolute(destination_root).lexically_normal();
#ifdef _WIN32
  if (!destination.native().starts_with(L"\\\\?\\")) {
    destination = destination.native().starts_with(L"\\\\")
        ? L"\\\\?\\UNC\\" + destination.native().substr(2)
        : L"\\\\?\\" + destination.native();
  }
#endif
  if (package_id.empty() || package_id.size() > 42 ||
      package_id.find_first_not_of("0123456789ABCDEF") != std::string::npos ||
      std::filesystem::exists(destination)) {
    throw std::runtime_error("Expected a package ID and a new staging root.");
  }
  rex::filesystem::StfsContainerDevice device("", package);
  if (!device.Initialize() || device.header().metadata.execution_info.title_id != kForzaHorizon ||
      device.header().metadata.content_type != rex::system::XContentType::kMarketplaceContent) {
    throw std::runtime_error("Expected Forza Horizon Marketplace content.");
  }
  rex::system::xam::ContentManager manager(nullptr, destination);
  rex::system::xam::XCONTENT_AGGREGATE_DATA data{};
  data.device_id = 1;
  data.content_type = rex::system::XContentType::kMarketplaceContent;
  data.title_id = kForzaHorizon;
  data.xuid = 0;
  data.set_file_name(package_id);
  data.set_display_name(device.header().metadata.display_name(rex::system::XLanguage::kEnglish));
  const auto root = destination / "0000000000000000" / "4D5309C9" / "00000002" / package_id;
  ExtractFiles(device, root, 3ull * 1024 * 1024 * 1024, 100000, cancelled);
  uint32_t license = 0;
  for (const auto& item : device.header().header.licenses) {
    if (item.license_flags) license |= item.license_bits;
  }
  if (manager.WriteContentHeaderFile(0, data, license) != X_ERROR_SUCCESS) {
    throw std::runtime_error("Could not write content header.");
  }
}

uint64_t ExtractForzaProfile(const std::filesystem::path& package,
                             const std::filesystem::path& destination,
                             std::function<bool()> cancelled) {
  if (!std::filesystem::is_regular_file(package) || std::filesystem::is_symlink(package) ||
      std::filesystem::file_size(package) > kSaveLimit ||
      std::filesystem::symlink_status(destination).type() != std::filesystem::file_type::not_found) {
    throw std::runtime_error("Expected a save package and a new staging folder.");
  }
  const auto header = rex::filesystem::StfsContainerDevice::ReadPackageHeader(package);
  if (!header || header->metadata.volume_type != rex::filesystem::XContentVolumeType::kStfs ||
      header->metadata.content_type != rex::system::XContentType::kSavedGame ||
      header->metadata.execution_info.title_id != kForzaHorizon ||
      header->metadata.data_file_count > 1) {
    throw std::runtime_error("Choose an Xbox 360 Forza Horizon ForzaProfile save package.");
  }
  const auto& descriptor = header->metadata.volume_descriptor.stfs;
  if (descriptor.file_table_block_count > kMaxSaveEntries / 64) {
    throw std::runtime_error("Too many ForzaProfile package entries.");
  }
  rex::filesystem::StfsContainerDevice device("", package);
  if (!device.Initialize()) throw std::runtime_error("This ForzaProfile package is incomplete or damaged.");
  ExtractFiles(device, destination, kSaveLimit, kMaxSaveEntries, cancelled);
  return header->metadata.profile_id;
}
}  // namespace pinyon_shift::content
