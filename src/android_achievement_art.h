#pragma once
#include <cstdint>
#include <filesystem>
#include <span>

namespace pinyon_shift::android {
// Artwork only: no profile data. Unknown, oversized or out-of-tree files are
// ignored.
void CacheAchievementArt(uint32_t id,
                         const std::filesystem::path &metadata_icon,
                         const std::filesystem::path &game_root,
                         const std::filesystem::path &cache,
                         std::span<const uint8_t> database_image);
}  // namespace pinyon_shift::android
