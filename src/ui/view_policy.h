#pragma once
#include <cstdint>

namespace pinyon_shift::ui {
// Host-time hysteresis: a single camera projection or map sample must not
// flip the whole presentation. Independent of game speed and render FPS.
class ViewPolicy {
 public:
  bool Update(bool game_recent,bool map_recent,uint64_t milliseconds) {
    if(!initialized_ || milliseconds<last_time_) {
      initialized_=true;narrow_=!game_recent;candidate_=narrow_;
      candidate_since_=last_time_=milliseconds;return true;
    }
    last_time_=milliseconds;
    const bool desired=!game_recent;
    if(desired==narrow_){candidate_=narrow_;candidate_since_=milliseconds;return false;}
    if(candidate_!=desired){candidate_=desired;candidate_since_=milliseconds;}
    const uint64_t delay=desired?(map_recent?180:800):150;
    if(milliseconds-candidate_since_<delay)return false;
    narrow_=desired;return true;
  }
  bool narrow() const{return narrow_;}
 private:
  bool initialized_=false,narrow_=true,candidate_=true;
  uint64_t candidate_since_=0,last_time_=0;
};
}
