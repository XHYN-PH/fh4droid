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
// Android counterpart of tools/fh1_archive_extract.cpp --dlc-files.
// Caller verifies the complete package against the supported catalog first.
void ExtractVerifiedDlc(const std::filesystem::path& package,
                        const std::filesystem::path& destination_root,
                        const std::string& package_id,
                        std::function<bool()> cancelled) {
      using rex::X_STATUS;
      using rex::X_RESULT;
      auto destination = std::filesystem::absolute(destination_root).lexically_normal();
#ifdef _WIN32
      // The runtime's content tree can exceed MAX_PATH in installed sources.
      if (!destination.native().starts_with(L"\\\\?\\"))
        destination = destination.native().starts_with(L"\\\\")
            ? L"\\\\?\\UNC\\" + destination.native().substr(2)
            : L"\\\\?\\" + destination.native();
#endif
      const std::string& id = package_id;
      if (id.empty() || id.size() > 42 || id.find_first_not_of("0123456789ABCDEF") != std::string::npos ||
          std::filesystem::exists(destination)) {
        throw std::runtime_error("expected a package ID and a new staging root");
      }
      rex::filesystem::StfsContainerDevice device("", package);
      if (!device.Initialize() || device.header().metadata.execution_info.title_id != 0x4D5309C9 ||
          device.header().metadata.content_type != rex::system::XContentType::kMarketplaceContent) {
        throw std::runtime_error("expected FH1 Marketplace content");
      }
      auto* root = device.ResolvePath("");
      if (!root) throw std::runtime_error("missing package root");
      rex::system::xam::ContentManager manager(nullptr, destination);
      rex::system::xam::XCONTENT_AGGREGATE_DATA data{};
      data.device_id = 1;
      data.content_type = rex::system::XContentType::kMarketplaceContent;
      data.title_id = 0x4D5309C9;
      data.xuid = 0;
      data.set_file_name(id);
      data.set_display_name(device.header().metadata.display_name(rex::system::XLanguage::kEnglish));
      const auto package_root = destination / "0000000000000000" / "4D5309C9" / "00000002" / id;
      std::queue<rex::filesystem::Entry*> entries;
      std::set<std::filesystem::path> paths;
      entries.push(root);
      std::vector<uint8_t> buffer(4 * 1024 * 1024);
      size_t visited = 0;
      uint64_t expanded = 0;
      while (!entries.empty()) {
        if (cancelled && cancelled()) throw std::runtime_error("Content operation cancelled");
        if (++visited > 100000) throw std::runtime_error("too many package entries");
        auto* entry = entries.front();
        entries.pop();
        for (const auto& child : entry->children()) entries.push(child.get());
        if (entry == root) continue;
        // Validate individual components as well as the complete guest path.
        const auto& name = entry->name();
        if (name.empty() || name == "." || name == ".." || name.back() == '.' ||
            name.back() == ' ' || name.find_first_of("\\/:<>\"|?*") != std::string::npos ||
            std::any_of(name.begin(), name.end(), [](unsigned char c) { return c < 32; })) {
          throw std::runtime_error("unsafe package member name");
        }
        auto relative = std::filesystem::path(rex::string::utf8_fix_path_separators(entry->path()));
        for (const auto& component : relative)
          if (component == ".." || component == ".") throw std::runtime_error("unsafe package path");
        if (relative.is_absolute() || relative.has_root_path() ||
            !paths.insert(relative).second) throw std::runtime_error("unsafe or duplicate package path");
        const auto output_path = package_root / relative;
        if (entry->attributes() & rex::filesystem::kFileAttributeDirectory) {
          std::filesystem::create_directories(output_path);
          continue;
        }
        expanded += entry->size();
        if (expanded > uint64_t(3)*1024*1024*1024) throw std::runtime_error("package exceeds the extraction limit");
        std::filesystem::create_directories(output_path.parent_path());
        rex::filesystem::File* raw = nullptr;
        if (entry->Open(rex::filesystem::FileAccess::kFileReadData, &raw) != X_STATUS_SUCCESS)
          throw std::runtime_error("could not open package member");
        std::unique_ptr<rex::filesystem::File, void(*)(rex::filesystem::File*)> input(
            raw, [](rex::filesystem::File* file) { file->Destroy(); });
        std::ofstream output(output_path, std::ios::binary);
        for (size_t offset = 0; offset < entry->size();) {
          if (cancelled && cancelled()) throw std::runtime_error("Content operation cancelled");
          const auto size = std::min(buffer.size(), entry->size() - offset);
          size_t read = 0;
          if (input->ReadSync(std::span<uint8_t>(buffer.data(), size), offset, &read) !=
                  X_STATUS_SUCCESS || read != size)
            throw std::runtime_error("incomplete package member");
          output.write(reinterpret_cast<const char*>(buffer.data()), read);
          if (!output) throw std::runtime_error("could not write package member");
          offset += read;
        }
        output.close();
        if (!output) throw std::runtime_error("could not finish package member");
      }
      uint32_t license = 0;
      for (const auto& item : device.header().header.licenses)
        if (item.license_flags) license |= item.license_bits;
      if (manager.WriteContentHeaderFile(0, data, license) != X_ERROR_SUCCESS)
        throw std::runtime_error("could not write content header");
}
}  // namespace pinyon_shift::content
