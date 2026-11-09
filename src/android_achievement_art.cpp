#include "android_achievement_art.h"

#include <array>
#include <fstream>
#include <vector>

namespace pinyon_shift::android {
namespace {
constexpr size_t kMaximum = 1024 * 1024;
bool Image(std::span<const uint8_t> bytes) {
  return bytes.size() >= 8 && bytes.size() <= kMaximum &&
         ((bytes[0] == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' &&
           bytes[3] == 'G') ||
          (bytes[0] == 0xff && bytes[1] == 0xd8));
}
}  // namespace
void CacheAchievementArt(uint32_t id,
                         const std::filesystem::path &metadata_icon,
                         const std::filesystem::path &game_root,
                         const std::filesystem::path &cache,
                         std::span<const uint8_t> database_image) {
  const auto target = cache / (std::to_string(id) + ".png");
  std::error_code error;
  if (std::filesystem::exists(target, error) || error) return;
  std::vector<uint8_t> metadata;
  if (!metadata_icon.empty()) {
    auto source =
        std::filesystem::weakly_canonical(game_root / metadata_icon, error);
    if (!error) {
      auto base = std::filesystem::weakly_canonical(game_root, error);
      const auto relative = source.lexically_relative(base);
      if (!error && !relative.empty() && *relative.begin() != ".." &&
          source.extension() == ".png" &&
          std::filesystem::is_regular_file(source, error)) {
        auto size = std::filesystem::file_size(source, error);
        if (!error && size >= 8 && size <= kMaximum) {
          metadata.resize(size);
          std::ifstream input(source, std::ios::binary);
          if (!input.read(reinterpret_cast<char *>(metadata.data()),
                          std::streamsize(size)))
            metadata.clear();
        }
      }
    }
  }
  std::span<const uint8_t> image =
      Image(metadata) ? std::span<const uint8_t>(metadata) : database_image;
  if (!Image(image)) return;
  // Cache paths must stay below the configured state root, even when an
  // existing cache directory was replaced with a link.
  std::error_code link_error;
  if (std::filesystem::is_symlink(cache, link_error) ||
      std::filesystem::is_symlink(cache.parent_path(), link_error)) return;
  error.clear();
  std::filesystem::create_directories(cache, error);
  if (error || std::filesystem::is_symlink(cache, error)) return;
  auto stage = target;
  stage += ".part";
  if (std::filesystem::is_symlink(stage, error)) return;
  std::ofstream output(stage, std::ios::binary | std::ios::trunc);
  output.write(reinterpret_cast<const char *>(image.data()),
               std::streamsize(image.size()));
  output.close();
  if (output) std::filesystem::rename(stage, target, error);
}
}  // namespace pinyon_shift::android
