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
      map_pending_=false;
      candidate_since_=last_time_=milliseconds;return true;
    }
    // Do not count suspension (or another long sampling gap) as evidence
    // that a pending scene transition continued while we weren't observing it.
    if(milliseconds-last_time_>1000) {
      candidate_=narrow_;candidate_since_=milliseconds;map_pending_=false;
    }
    last_time_=milliseconds;
    const bool desired=!game_recent;
    if(desired==narrow_){candidate_=narrow_;candidate_since_=milliseconds;map_pending_=false;return false;}
    if(candidate_!=desired){candidate_=desired;candidate_since_=milliseconds;}
    // Map evidence gets its own continuous interval. A late map sample
    // must not shorten the already-running, ordinary camera-gap grace.
    if(desired && map_recent) {
      if(!map_pending_){map_pending_=true;map_since_=milliseconds;}
    } else {
      map_pending_=false;
    }
    const bool map_ready=map_pending_ && milliseconds-map_since_>=180;
    const uint64_t delay=desired?800:150;
    if(milliseconds-candidate_since_<delay && !map_ready)return false;
    narrow_=desired;return true;
  }
  bool narrow() const{return narrow_;}
 private:
  bool initialized_=false,narrow_=true,candidate_=true;
  bool map_pending_=false;
  uint64_t candidate_since_=0,last_time_=0,map_since_=0;
};
}
