#pragma once
#include <algorithm>
#include <cmath>
#include <span>
#include <string>
#include <vector>
#include <rex/ui/immediate_drawer.h>
#include "ui/touch_icons.h"
#include "ui/touch_artwork.h"

namespace pinyon_shift::ui {
// Shared production mesh generation. HostUi submits these batches unchanged;
// the standalone visual test consumes the same vertices, UVs and PNG bytes.
struct TouchOverlayDisc {
  float x=0,y=0,radius=0;
  std::string label;
  bool pressed=false;
  float opacity=.55f;
  bool driving_icons=true,button_hints=true;
  float icon_opacity=.95f;
  bool thumb_knob=false;
  float amount=0;
};
// Build both halves of a stick with its real identity: the base owns the
// modern icon/caption, the moving knob stays unlabelled in driving mode.
inline std::vector<TouchOverlayDisc> TouchOverlayForShapes(const auto& shapes,float opacity){
  std::vector<TouchOverlayDisc> discs;
  for(const auto& shape:shapes){
    discs.push_back({shape.x,shape.y,shape.radius,shape.label,
        shape.pressed&&!shape.stick,opacity*shape.opacity,shape.driving_icons,
        shape.button_hints,shape.icon_opacity*shape.opacity,false,shape.amount});
    if(shape.stick)discs.push_back({shape.stick_x,shape.stick_y,shape.radius*.55f,
        shape.label,shape.pressed,opacity*shape.opacity,shape.driving_icons,
        shape.button_hints,shape.icon_opacity*shape.opacity,true});
  }
  return discs;
}
struct TouchOverlayBatch {
  std::vector<rex::ui::ImmediateVertex> vertices;
  std::vector<uint16_t> indices;
  bool artwork=false;
};
inline constexpr uint32_t TouchRgba(uint32_t r,uint32_t g,uint32_t b,uint32_t a){return r|g<<8|b<<16|a<<24;}
inline void AppendTouchFrame(std::vector<TouchOverlayBatch>& batches_, float x, float y, float width, float height, bool rectangular,
                             float thickness, uint32_t fill, uint32_t outline) {
  if (width <= thickness || height <= thickness) return;
  if (batches_.empty() || batches_.back().artwork ||
      batches_.back().vertices.size() > 60000) batches_.push_back(TouchOverlayBatch{});
  auto& batch = batches_.back();
  const float corner = rectangular ? std::min(width, height) * .25f : std::min(width, height);
  constexpr unsigned kSteps = 8, kPoints = 4 * (kSteps + 1);
  const auto base = uint16_t(batch.vertices.size());
  batch.vertices.push_back({x, y, .5f, .5f, fill});
  // A filled fan followed by a non-overlapping outline strip, in window pixels.
  for (unsigned ring = 0; ring < 2; ++ring) {
    for (unsigned q = 0; q < 4; ++q) {
      const float cx = x + (q == 0 || q == 3 ? 1.f : -1.f) * (width - corner);
      const float cy = y + (q >= 2 ? -1.f : 1.f) * (height - corner);
      for (unsigned i = 0; i <= kSteps; ++i) {
        const float angle = (float(q) + float(i) / kSteps) * 1.5707963268f;
        const float r = std::max(0.f, corner - (ring ? thickness : 0.f));
        batch.vertices.push_back({cx + std::cos(angle) * r, cy + std::sin(angle) * r,
                                  .5f, .5f, ring ? fill : outline});
      }
    }
  }
  // Use the inner ring for the fill; the border keeps its intended alpha.
  for (unsigned i = 0; i < kPoints; ++i) {
    const auto next = uint16_t((i + 1) % kPoints);
    const auto outer = uint16_t(base + 1 + i), outer_next = uint16_t(base + 1 + next);
    const auto inner = uint16_t(outer + kPoints), inner_next = uint16_t(outer_next + kPoints);
    batch.indices.insert(batch.indices.end(), {base, inner, inner_next});
    // Separate outline vertices avoid interpolating the fill colour into it.
    const auto edge = uint16_t(batch.vertices.size());
    for (const auto v : {outer, inner, outer_next, inner_next}) {
      auto vertex = batch.vertices[v]; vertex.color = outline; batch.vertices.push_back(vertex);
    }
    for (uint16_t v : {0, 1, 2, 2, 1, 3}) batch.indices.push_back(uint16_t(edge + v));
  }
}

inline std::vector<TouchOverlayBatch> BuildTouchOverlay(std::span<const TouchOverlayDisc> discs) {
  std::vector<TouchOverlayBatch> batches_;
  for (const auto& disc : discs) {
    const auto* icon = FindTouchIcon(disc.label);
    const float opacity = std::clamp(disc.opacity, 0.f, 1.f);
    if(opacity<=0 || !icon)continue;
    const float ink=std::clamp(disc.icon_opacity,0.f,1.f);
    const float stroke=std::max(1.1f,disc.radius*.022f);
    const uint32_t fill=disc.pressed?TouchRgba(255,108,53,int(220*opacity)):disc.thumb_knob?TouchRgba(220,226,235,int(170*opacity)):TouchRgba(20,27,37,int(190*opacity));
    const uint32_t border=disc.pressed?TouchRgba(255,145,90,int(255*ink)):TouchRgba(184,201,220,int(150*opacity));
    const bool pedal=disc.driving_icons&&!disc.thumb_knob&&
        (disc.label=="RT"||disc.label=="LT"||disc.label=="LB");
    if(!pedal)AppendTouchFrame(batches_,disc.x,disc.y,disc.radius*icon->width,disc.radius*icon->height,
                   icon->rectangular,stroke,fill,border);
    if(disc.driving_icons&&!disc.thumb_knob&&disc.amount>0){
      const float w=disc.radius*.85f*std::clamp(disc.amount,0.f,1.f);
      AppendTouchFrame(batches_,disc.x-disc.radius*.425f+w*.5f,disc.y+disc.radius*.65f,w,disc.radius*.065f,true,0,TouchRgba(255,165,94,int(240*ink)),0);
    }
    if(disc.thumb_knob&&disc.driving_icons)continue;
    // In classic mode the stick letters follow the knob; modern captions
    // remain on the stationary base so they do not drift during steering.
    if(!disc.driving_icons&&!disc.thumb_knob&&(disc.label=="LS"||disc.label=="RS"))continue;
    const size_t index=size_t(icon-kTouchIcons);
    const auto sprite=[&](unsigned plane){
      const float u=float(index%kTouchArtworkColumns)/kTouchArtworkColumns;
      const float v=float(index/kTouchArtworkColumns+plane*kTouchArtworkRows)/(kTouchArtworkRows*3);
      if(batches_.empty()||!batches_.back().artwork||batches_.back().vertices.size()>60000)batches_.push_back(TouchOverlayBatch{.artwork=true});
      auto& batch=batches_.back();const auto base=uint16_t(batch.vertices.size());
      const float right=u+1.f/kTouchArtworkColumns,bottom=v+1.f/(kTouchArtworkRows*3);
      const auto color=pedal&&disc.pressed?TouchRgba(255,190,125,int(255*ink)):TouchRgba(255,255,255,int(255*ink));
      batch.vertices.push_back({disc.x-disc.radius,disc.y-disc.radius,u,v,color});
      batch.vertices.push_back({disc.x+disc.radius,disc.y-disc.radius,right,v,color});
      batch.vertices.push_back({disc.x+disc.radius,disc.y+disc.radius,right,bottom,color});
      batch.vertices.push_back({disc.x-disc.radius,disc.y+disc.radius,u,bottom,color});
      for(uint16_t corner:{0,1,2,0,2,3})batch.indices.push_back(uint16_t(base+corner));
    };
    if(disc.thumb_knob||(disc.label!="LS"&&disc.label!="RS"))sprite(disc.driving_icons?0:2);
    if(disc.driving_icons && disc.button_hints)sprite(1);
  }
  return batches_;
}
} // namespace pinyon_shift::ui
