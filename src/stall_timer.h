#pragma once

#include <algorithm>
#include <cstdint>

namespace pinyon_shift::stall {

// Timing only; callers synchronize lifecycle updates and watchdog polling.
// Background time must never qualify as a missing-frame stall. A resume
// starts a fresh grace interval even if no title frame has arrived yet.
class StallTimer {
 public:
  void SetPaused(bool paused, int64_t now) {
    if (paused_ == paused) return;
    paused_ = paused;
    if (!paused) {
      resumed_at_ = now;
      dumped_for_ = -1;
    }
  }

  // Returns active elapsed nanoseconds once per stall, or zero when not due.
  int64_t Poll(int64_t now, int64_t last_frame, int64_t threshold) {
    if (paused_ || last_frame <= 0 || threshold <= 0) return 0;
    const int64_t baseline = std::max(last_frame, resumed_at_);
    if (now < baseline || baseline == dumped_for_) return 0;
    const int64_t elapsed = now - baseline;
    if (elapsed < threshold) return 0;
    dumped_for_ = baseline;
    return elapsed;
  }

 private:
  bool paused_ = false;
  int64_t resumed_at_ = 0;
  int64_t dumped_for_ = -1;
};

}  // namespace pinyon_shift::stall
