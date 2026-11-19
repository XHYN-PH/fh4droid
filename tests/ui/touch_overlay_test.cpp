// Software readback of the production overlay meshes and embedded PNG.
// With an output argument, writes a PPM artwork proof (not a phone screenshot).
#ifdef NDEBUG
#undef NDEBUG
#endif
#include "ui/touch_overlay.h"
#include "ui/touch_pad.h"
#include <array>
#include <cassert>
#include <cstdio>
#include <fstream>
#define STB_IMAGE_IMPLEMENTATION
#define STBI_ONLY_PNG
#include <stb_image.h>
using namespace pinyon_shift::ui;
using rex::ui::ImmediateVertex;
namespace {
float Edge(const ImmediateVertex& a,const ImmediateVertex& b,float x,float y){return (b.x-a.x)*(y-a.y)-(b.y-a.y)*(x-a.x);}
bool TopLeft(const ImmediateVertex& a,const ImmediateVertex& b){return b.y<a.y||(b.y==a.y&&b.x>a.x);}
}
int main(int argc,char** argv){
  int width=0,height=0,channels=0;
  auto* png=stbi_load_from_memory(kTouchArtworkPng,sizeof(kTouchArtworkPng),&width,&height,&channels,4);
  assert(png&&width==640&&height==2400);
  // Every text-only action label is nonempty and fits its saved touch target.
  for(unsigned i=0;i<std::size(kTouchIcons);i++){
    unsigned count=0;for(unsigned y=0;y<kTouchArtworkTile;y++)for(unsigned x=0;x<kTouchArtworkTile;x++){
      unsigned px=(i%kTouchArtworkColumns)*kTouchArtworkTile+x,py=(i/kTouchArtworkColumns)*kTouchArtworkTile+y;
      if(png[(py*width+px)*4+3]<32)continue;++count;const auto& icon=kTouchIcons[i];
      float nx=((x+.5f)/kTouchArtworkTile*2-1)/icon.width,ny=((y+.5f)/kTouchArtworkTile*2-1)/icon.height;
      assert(std::abs(nx)<.98f&&std::abs(ny)<.98f);
    }assert(count>30);
  }
  // Captions remain inside their backing, including long Back/Start names.
  for(unsigned i=0;i<std::size(kTouchIcons);i++)for(unsigned y=0;y<kTouchArtworkTile;y++)for(unsigned x=0;x<kTouchArtworkTile;x++){
    unsigned px=(i%kTouchArtworkColumns)*kTouchArtworkTile+x;
    unsigned py=(i/kTouchArtworkColumns+kTouchArtworkRows)*kTouchArtworkTile+y;
    if(png[(py*width+px)*4+3]<32)continue;
    const auto& icon=kTouchIcons[i];
    float nx=((x+.5f)/kTouchArtworkTile*2-1)/icon.width,ny=((y+.5f)/kTouchArtworkTile*2-1)/icon.height;
    assert(icon.rectangular?(std::abs(nx)<.98f&&std::abs(ny)<.98f):nx*nx+ny*ny<.98f*.98f);
  }
  std::vector<TouchOverlayDisc> discs;
  for(unsigned i=0;i<std::size(kTouchIcons);i++)for(unsigned state=0;state<2;state++){
    TouchOverlayDisc disc;disc.label=kTouchIcons[i].name;disc.x=(i%5)*320+80+160*state;disc.y=(i/5)*240+120;disc.radius=66;disc.pressed=state;bool stick=disc.label=="LS"||disc.label=="RS";if(stick)disc.pressed=false;discs.push_back(disc);
    if(stick){disc.thumb_knob=true;disc.pressed=state;disc.radius*=.55f;discs.push_back(disc);}
  }
  auto batches=BuildTouchOverlay(discs);
  size_t sprite_count=0;
  for(const auto& batch:batches){
    assert(batch.indices.size()%3==0);
    for(auto index:batch.indices)assert(index<batch.vertices.size());
    for(auto v:batch.vertices)assert(std::isfinite(v.x)&&std::isfinite(v.y)&&v.u>=0&&v.u<=1&&v.v>=0&&v.v<=1);
    if(batch.artwork){
      assert(batch.vertices.size()%4==0);
      for(size_t first=0;first<batch.vertices.size();first+=4){
        const auto& a=batch.vertices[first];const auto& b=batch.vertices[first+1];const auto& d=batch.vertices[first+3];
        assert(std::abs((b.x-a.x)-(d.y-a.y))<.001f); // Every atlas sprite stays square.
      }
      sprite_count+=batch.vertices.size()/4;
    }
  }
  assert(sprite_count==76); // Live sticks use geometry plus a caption, not a painted second knob.
  // Text pedals retain a visible backing in both display styles.
  for(const auto* name:{"RT","LT","LB"}){TouchOverlayDisc pedal;pedal.label=name;pedal.x=100;pedal.y=100;pedal.radius=60;bool backing=false,artwork=false;for(const auto& b:BuildTouchOverlay(std::span(&pedal,1))){backing|=!b.artwork;artwork|=b.artwork;}assert(backing&&artwork);}
  auto classic=discs;for(auto& d:classic)d.driving_icons=false;
  size_t classic_sprites=0;for(const auto& b:BuildTouchOverlay(classic))if(b.artwork)classic_sprites+=b.vertices.size()/4;
  assert(classic_sprites==40); // Stick letters belong to their moving knobs.
  auto knob=discs[0];knob.thumb_knob=true;for(const auto& b:BuildTouchOverlay(std::span(&knob,1)))assert(!b.artwork);
  auto classic_knob=knob;classic_knob.driving_icons=false;classic_knob.label="LS";
  size_t knob_letters=0;for(const auto& b:BuildTouchOverlay(std::span(&classic_knob,1)))if(b.artwork)knob_letters+=b.vertices.size()/4;
  assert(knob_letters==1);
  if(argc>1){
    const bool pedals=argc>2&&std::string_view(argv[2])=="--pedals";
    const int w=pedals?640:argc>2?1280:1600,h=pedals?500:argc>2?720:960;
    if(pedals){
      discs.clear();
      for(unsigned state=0;state<2;state++)for(unsigned i=0;i<2;i++){
        TouchOverlayDisc disc;disc.label=i?"RT":"LT";disc.x=160+i*320;disc.y=125+state*250;
        disc.radius=i?100.8f:82.8f;disc.pressed=state;discs.push_back(disc);
      }
      batches=BuildTouchOverlay(discs);
    }else if(argc>2){
      auto& pad=TouchPad::Get();pad.LoadLayout(argv[2]);
      discs=TouchOverlayForShapes(pad.Shapes(float(w),float(h)));
      batches=BuildTouchOverlay(discs);
    }
    std::vector<std::array<float,3>> pixels(w*h,{16,20,25});
    auto sample=[&](float u,float v,int component){
      float x=std::clamp(u*width-.5f,0.f,float(width-1)),y=std::clamp(v*height-.5f,0.f,float(height-1));int x0=int(x),y0=int(y),x1=std::min(width-1,x0+1),y1=std::min(height-1,y0+1);float tx=x-x0,ty=y-y0;
      return ((1-tx)*png[(y0*width+x0)*4+component]+tx*png[(y0*width+x1)*4+component])*(1-ty)+((1-tx)*png[(y1*width+x0)*4+component]+tx*png[(y1*width+x1)*4+component])*ty;
    };
    for(const auto& b:batches)for(size_t t=0;t<b.indices.size();t+=3){
      auto a=b.vertices[b.indices[t]],c=b.vertices[b.indices[t+1]],d=b.vertices[b.indices[t+2]];
      float area=Edge(a,c,d.x,d.y);if(area==0)continue;if(area<0){std::swap(c,d);area=-area;}
      int left=std::max(0,int(std::floor(std::min({a.x,c.x,d.x})))),right=std::min(w,int(std::ceil(std::max({a.x,c.x,d.x}))));
      int top=std::max(0,int(std::floor(std::min({a.y,c.y,d.y})))),bottom=std::min(h,int(std::ceil(std::max({a.y,c.y,d.y}))));
      for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){
        float e0=Edge(c,d,x+.5f,y+.5f),e1=Edge(d,a,x+.5f,y+.5f),e2=Edge(a,c,x+.5f,y+.5f);
        if(e0<0||(e0==0&&!TopLeft(c,d))||e1<0||(e1==0&&!TopLeft(d,a))||e2<0||(e2==0&&!TopLeft(a,c)))continue;
        float u=(a.u*e0+c.u*e1+d.u*e2)/area,v=(a.v*e0+c.v*e1+d.v*e2)/area;
        float alpha=float(a.color>>24)/255*(b.artwork?sample(u,v,3)/255:1);
        for(int channel=0;channel<3;channel++){float value=float((a.color>>(channel*8))&255)*(b.artwork?sample(u,v,channel)/255:1);pixels[y*w+x][channel]=value*alpha+pixels[y*w+x][channel]*(1-alpha);}
      }
    }
    std::ofstream out(argv[1],std::ios::binary);out<<"P6\n"<<w<<' '<<h<<"\n255\n";
    for(auto pixel:pixels)for(float channel:pixel)out.put(char(std::clamp(std::lround(channel),0l,255l)));
    assert(out.good());
  }
  stbi_image_free(png);
  std::puts("PASS: 20 production text controls, normal/pressed/classic states, UVs, captions and unlabelled stick knobs");
}
