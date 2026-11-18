#pragma once

// Host services the app needs from the operating system, behind one
// interface so the app's sources stay free of Windows.h (NP-12.2).

#include <array>
#include <cstddef>
#include <cstdint>
#include <filesystem>
#include <functional>
#include <optional>
#include <span>
#include <string>
#include <string_view>

namespace pinyon_shift::platform {

// Replaces `destination` with `temporary` in one step: readers see the old
// file or the new one, never a partial write. On Windows the move is also
// written through to disk. On failure `temporary` is removed and `destination`
// is left as it was.
bool ReplaceFileAtomically(const std::filesystem::path& temporary,
                           const std::filesystem::path& destination);
// A unique, exclusively created sibling, flushed before replacement.
bool WriteFileAtomically(const std::filesystem::path& destination, std::string_view bytes);
// Uses the same byte-range lock as Java FileChannel.lock(). The callback
// must not acquire another settings lock. Readers may use atomic snapshots.
bool WithFileLock(const std::filesystem::path& path, const std::function<bool()>& work);

// An environment variable's value (UTF-8), or nothing when it is not set.
std::optional<std::string> EnvironmentVariable(const char* name);

// Whether the environment variable is set to exactly "1".
bool EnvironmentFlag(const char* name);

// A non-empty environment variable as an absolute, normalized path.
std::optional<std::filesystem::path> EnvironmentPath(const char* name);

// The running executable's path, or an empty path when it cannot be found.
std::filesystem::path ExecutablePath();

// This process's and the calling thread's operating system IDs.
uint64_t ProcessId();
uint64_t ThreadId();

// SHA-256 of `bytes` (the SDK's vendored implementation, on every host;
// platform/sha256.cpp).
std::array<uint8_t, 32> Sha256(std::span<const std::byte> bytes);

// Tells the player about an error the app cannot continue from: a message
// box on Windows, standard error elsewhere. Text is UTF-8.
void ShowFatalError(std::string_view title, std::string_view text);

// Ends the process at once with `code`, without running static destructors
// (the runtime's threads may still hold its objects).
[[noreturn]] void ExitImmediately(uint32_t code);

// The refresh rate, in Hz, of the display showing `native_window` (the
// window's native handle), when the platform reports one.
std::optional<uint32_t> DisplayRefreshRate(void* native_window);

}  // namespace pinyon_shift::platform
