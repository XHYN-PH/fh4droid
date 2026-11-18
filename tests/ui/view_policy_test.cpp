#include "ui/view_policy.h"
#include <cstdio>

int main(){
  int failed=0;
  const auto check=[&](bool ok,const char* why){if(!ok){std::fprintf(stderr,"%s\n",why);++failed;}};
  pinyon_shift::ui::ViewPolicy p;
  check(p.Update(false,false,100)&&p.narrow(),"startup frontend is correctly framed");
  check(!p.Update(true,false,200)&&p.narrow(),"one gameplay camera is not enough to widen");
  check(!p.Update(false,true,220)&&p.narrow(),"a transient camera does not flip the frontend");
  p.Update(true,false,300);check(p.Update(true,false,450)&&!p.narrow(),"sustained gameplay widens");
  check(!p.Update(false,true,460)&&!p.narrow(),"one map projection does not shrink gameplay");
  check(!p.Update(true,false,490)&&!p.narrow(),"short camera gaps retain the wide frame");
  p.Update(false,true,500);check(!p.Update(false,true,679)&&!p.narrow(),"map transition respects its grace interval");
  check(p.Update(false,true,680)&&p.narrow(),"a sustained map uses original framing");
  p.Update(true,false,700);check(p.Update(true,false,850)&&!p.narrow(),"returning from the map restores wide gameplay");
  p.Update(false,false,900);check(!p.Update(false,false,1699)&&!p.narrow(),"brief non-gameplay gaps do not oscillate");
  check(p.Update(false,false,1700)&&p.narrow(),"a sustained frontend returns to original framing");
  check(p.Update(false,false,1)&&p.narrow(),"clock reset does not underflow timing");
  pinyon_shift::ui::ViewPolicy late;
  late.Update(true,false,100);late.Update(false,false,200);
  check(!late.Update(false,true,500)&&!late.narrow(),"a late single map sample cannot inherit the camera-gap timer");
  check(!late.Update(true,false,520)&&!late.narrow(),"returning gameplay after a map flash retains wide framing");
  pinyon_shift::ui::ViewPolicy interrupted;
  interrupted.Update(true,false,100);interrupted.Update(false,true,200);interrupted.Update(false,false,300);
  check(!interrupted.Update(false,true,400)&&!interrupted.narrow(),"interrupted map evidence must start a fresh grace interval");
  check(!interrupted.Update(false,true,579)&&!interrupted.narrow(),"map grace starts at the latest continuous map interval");
  check(interrupted.Update(false,true,580)&&interrupted.narrow(),"sustained map still transitions after its full grace interval");
  pinyon_shift::ui::ViewPolicy resumed;
  resumed.Update(true,false,100);resumed.Update(false,false,200);
  check(!resumed.Update(false,false,41200)&&!resumed.narrow(),"a background-length update gap cannot expire a pending view candidate");
  check(!resumed.Update(true,false,41230)&&!resumed.narrow(),"fresh gameplay after a long update gap remains wide");
  pinyon_shift::ui::ViewPolicy frontend;
  frontend.Update(true,false,100);frontend.Update(false,false,200);
  for(uint64_t time=300;time<1000;time+=100)frontend.Update(false,time%200==0,time);
  check(frontend.Update(false,false,1000)&&frontend.narrow(),"intermittent map signals do not postpone an ordinary frontend transition");
  check(!frontend.Update(true,false,50000)&&frontend.narrow(),"long gaps preserve committed narrow framing too");
  check(!frontend.Update(true,false,50149)&&frontend.narrow(),"resumed gameplay needs fresh continuous evidence");
  check(frontend.Update(true,false,50150)&&!frontend.narrow(),"resumed gameplay widens after its normal grace");
  if(!failed)std::puts("PASS: presentation hysteresis and map/game transitions");
  return failed?1:0;
}
