#include "android_achievement_art.h"
#include <array>
#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <vector>

int main(){
  namespace fs=std::filesystem;
  using pinyon_shift::android::CacheAchievementArt;
  const auto root=fs::temp_directory_path()/("fh4droid-art-"+std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
  const auto game=root/"game",cache=root/"cache/icons";fs::create_directories(game);
  const std::array<uint8_t,8> png={0x89,'P','N','G',13,10,26,10};
  auto write=[&](const fs::path& path){std::ofstream out(path,std::ios::binary);out.write(reinterpret_cast<const char*>(png.data()),png.size());};
  int failures=0;auto check=[&](bool value,const char* what){if(!value){std::cerr<<what<<'\n';failures++;}};
  write(game/"icon.png");write(root/"outside.png");
  CacheAchievementArt(1,"icon.png",game,cache,{});check(fs::file_size(cache/"1.png")==png.size(),"valid local art is cached");
  CacheAchievementArt(2,"../outside.png",game,cache,{});check(!fs::exists(cache/"2.png"),"metadata cannot read outside the game root");
  CacheAchievementArt(3,{},game,cache,png);check(fs::file_size(cache/"3.png")==png.size(),"database artwork works without extracted textures");
  std::vector<uint8_t> huge(1024*1024+1);std::copy(png.begin(),png.end(),huge.begin());
  CacheAchievementArt(4,{},game,cache,huge);check(!fs::exists(cache/"4.png"),"oversized artwork is rejected");
  const std::array<uint8_t,3> invalid={'b','a','d'};CacheAchievementArt(5,{},game,cache,invalid);check(!fs::exists(cache/"5.png"),"invalid artwork is rejected");
  std::error_code error;fs::create_symlink(root/"outside.png",game/"linked.png",error);
  if(!error){CacheAchievementArt(6,"linked.png",game,cache,{});check(!fs::exists(cache/"6.png"),"linked metadata cannot escape the game root");}
  fs::create_directories(root/"external");error.clear();fs::create_directory_symlink(root/"external",root/"linked-cache",error);
  if(!error){CacheAchievementArt(7,{},game,root/"linked-cache/icons",png);check(!fs::exists(root/"external/icons/7.png"),"linked cache parents are refused");}
  fs::remove_all(root);std::cout<<"Achievement artwork: "<<failures<<" failures\n";return failures?1:0;
}
