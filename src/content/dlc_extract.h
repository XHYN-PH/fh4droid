#pragma once
#include <cstdint>
#include <filesystem>
#include <functional>
#include <string>
namespace pinyon_shift::content {
void ExtractVerifiedDlc(const std::filesystem::path& package,
                        const std::filesystem::path& destination_root,
                        const std::string& package_id,
                        std::function<bool()> cancelled = {});
uint64_t ExtractForzaProfile(const std::filesystem::path& package,
                             const std::filesystem::path& destination,
                             std::function<bool()> cancelled = {});
}
