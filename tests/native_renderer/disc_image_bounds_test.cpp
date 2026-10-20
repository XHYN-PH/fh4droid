#include <rex/filesystem/devices/disc_image_device.h>
#include <cassert>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <vector>
#include <chrono>
namespace {
void Put32(std::vector<unsigned char>& bytes,size_t at,uint32_t n) {
  for(unsigned i=0;i<4;++i)bytes[at+i]=static_cast<unsigned char>(n>>(i*8));
}
bool Open(const std::filesystem::path& file,const std::vector<unsigned char>& bytes) {
  {std::ofstream out(file,std::ios::binary);out.write(reinterpret_cast<const char*>(bytes.data()),bytes.size());}
  rex::filesystem::DiscImageDevice device("\\Device\\Test",file);
  return device.Initialize();
}
}
int main(int argc,char** argv) {
  std::filesystem::path file=argc==2 ? std::filesystem::path(argv[1]) :
      std::filesystem::temp_directory_path() /
          ("fh4droid-disc-bounds-" + std::to_string(
              std::chrono::steady_clock::now().time_since_epoch().count()) + ".iso");
  std::vector<unsigned char> good(60*2048);
  std::memcpy(good.data()+32*2048,"MICROSOFT*XBOX*MEDIA",20);
  Put32(good,32*2048+20,40);Put32(good,32*2048+24,2048);
  Put32(good,40*2048+4,50);Put32(good,40*2048+8,5);
  good[40*2048+12]=0x20;good[40*2048+13]=11;
  std::memcpy(good.data()+40*2048+14,"default.xex",11);
  assert(Open(file,good));
  auto bad=good;Put32(bad,40*2048+8,999999);assert(!Open(file,bad));
  bad=good;bad[40*2048+2]=255;bad[40*2048+3]=127;assert(!Open(file,bad));
  bad=good;Put32(bad,40*2048+4,40);Put32(bad,40*2048+8,2048);bad[40*2048+12]=16;assert(!Open(file,bad));
  bad=good;Put32(bad,32*2048+24,0xffffffff);assert(!Open(file,bad));
  bad.resize(32*2048+20);assert(!Open(file,bad));
  std::filesystem::remove(file);
  std::cout<<"PASS: native disc reader accepts valid images and rejects bounds/cycles\n";
}
