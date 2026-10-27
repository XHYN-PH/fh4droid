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
  if(!failed)std::puts("PASS: presentation hysteresis and map/game transitions");
  return failed?1:0;
}
