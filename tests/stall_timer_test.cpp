#include "stall_timer.h"

#include <cstdio>

int main() {
  int failures = 0;
  const auto check = [&](bool ok, const char* why) {
    if (!ok) { std::fprintf(stderr, "%s\n", why); ++failures; }
  };
  constexpr int64_t second = 1000000000;
  constexpr int64_t limit = 20 * second;
  pinyon_shift::stall::StallTimer timer;
  check(timer.Poll(100 * second, 0, limit) == 0, "no alarm before first frame");
  check(timer.Poll(20 * second, second, limit) == 0, "respect active grace");
  check(timer.Poll(21 * second, second, limit) == limit, "detect a real missing-frame stall");
  check(timer.Poll(22 * second, second, limit) == 0, "one dump per stall");
  check(timer.Poll(42 * second, 22 * second, limit) == limit, "a new frame rearms detection");

  pinyon_shift::stall::StallTimer lifecycle;
  lifecycle.SetPaused(true, 10 * second);
  check(lifecycle.Poll(51 * second, second, limit) == 0, "background time is not a stall");
  lifecycle.SetPaused(true, 51 * second);
  lifecycle.SetPaused(false, 51 * second);
  check(lifecycle.Poll(51 * second, second, limit) == 0, "resume excludes the whole background interval");
  check(lifecycle.Poll(70 * second, second, limit) == 0, "resume grants a complete grace interval");
  lifecycle.SetPaused(false, 70 * second);
  check(lifecycle.Poll(71 * second, second, limit) == limit, "missing frames after resume still trigger a dump");
  check(lifecycle.Poll(72 * second, second, limit) == 0, "resumed stall is dumped once");
  check(lifecycle.Poll(95 * second, 75 * second, limit) == limit, "new frames replace the resume baseline");
  lifecycle.SetPaused(true, 96 * second);
  lifecycle.SetPaused(false, 150 * second);
  check(lifecycle.Poll(170 * second, 75 * second, limit) == limit, "a second resume rearms even without new frames");

  pinyon_shift::stall::StallTimer disabled;
  check(disabled.Poll(100 * second, second, 0) == 0, "zero disables automatic dumps");
  check(disabled.Poll(100 * second, second, -1) == 0, "negative thresholds are disabled");
  check(disabled.Poll(second, 2 * second, limit) == 0, "clock reversal cannot underflow elapsed time");
  check(disabled.Poll(22 * second, 2 * second, limit) == limit, "disabled polling does not consume a future dump");
  if (!failures) std::puts("PASS: stall timing, lifecycle pause and resume");
  return failures ? 1 : 0;
}
