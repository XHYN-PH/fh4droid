#include <rex/filesystem/devices/disc_image_device.h>
#include <rex/filesystem/entry.h>
#include <rex/filesystem/file.h>
#include <array>
#include <cassert>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <vector>
#include <chrono>
#include <random>
#include <algorithm>
#if !REX_PLATFORM_WIN32
#include <fcntl.h>
#include <unistd.h>
#endif

namespace {
void Put32(std::vector<unsigned char>& bytes,size_t at,uint32_t n) {
  for(unsigned i=0;i<4;++i)bytes[at+i]=static_cast<unsigned char>(n>>(i*8));
}
void WriteImage(const std::filesystem::path& file,const std::vector<unsigned char>& bytes) {
  std::ofstream out(file,std::ios::binary);
  out.write(reinterpret_cast<const char*>(bytes.data()),bytes.size());
}
bool Open(const std::filesystem::path& file,const std::vector<unsigned char>& bytes) {
  WriteImage(file,bytes);
  rex::filesystem::DiscImageDevice device("\\Device\\Test",file);
  const bool opened = device.Initialize();
#if !REX_PLATFORM_WIN32
  const int descriptor = open(file.c_str(), O_RDONLY | O_CLOEXEC);
  assert(descriptor >= 0);
  rex::filesystem::DiscImageDevice read_device("\\Device\\Test", descriptor);
  close(descriptor);
  assert(read_device.Initialize() == opened);
#endif
  return opened;
}
#if !REX_PLATFORM_WIN32
void CheckDescriptorReads(const std::filesystem::path& file, const std::vector<unsigned char>& bytes) {
  using rex::X_STATUS;
  WriteImage(file, bytes);
  const int descriptor = open(file.c_str(), O_RDONLY | O_CLOEXEC);
  assert(descriptor >= 0);
  assert(lseek(descriptor, 17, SEEK_SET) == 17);
  rex::filesystem::DiscImageDevice disc("\\Device\\Test", descriptor);
  assert(disc.Initialize());
  assert(lseek(descriptor, 0, SEEK_CUR) == 17);
  close(descriptor);
  auto* entry = disc.ResolvePath("default.xex");
  assert(entry && !entry->can_map());
  assert(!entry->OpenMapped(rex::memory::MappedMemory::Mode::kRead));
  rex::filesystem::File* input = nullptr;
  assert(entry->Open(rex::filesystem::FileAccess::kGenericRead, &input) == X_STATUS_SUCCESS);
  std::array<uint8_t, 6> content{};
  size_t read = 0;
  assert(input->ReadSync(content, 1, &read) == X_STATUS_SUCCESS);
  assert(read == 4 && std::memcmp(content.data(), "est!", 4) == 0);
  assert(input->ReadSync(content, 5, &read) == X_STATUS_END_OF_FILE && read == 0);
  // A provider may lose access or the selected file may change mid-session.
  std::filesystem::resize_file(file, 50 * 2048);
  assert(input->ReadSync(content, 0, &read) == X_STATUS_UNSUCCESSFUL && read == 0);
  input->Destroy();
}
#endif
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
  std::memcpy(good.data()+50*2048,"Test!",5);
  assert(Open(file,good));
  {
    rex::filesystem::DiscImageDevice mapped("\\Device\\Test", file);
    assert(mapped.Initialize());
    auto* entry = mapped.ResolvePath("default.xex");
    assert(entry && entry->can_map());
    auto tail = entry->OpenMapped(rex::memory::MappedMemory::Mode::kRead, 4);
    assert(tail && tail->size() == 1 && *tail->data() == '!');
    assert(!entry->OpenMapped(rex::memory::MappedMemory::Mode::kRead, 6));
  }
#if !REX_PLATFORM_WIN32
  CheckDescriptorReads(file,good);
#endif
  auto bad=good;Put32(bad,40*2048+8,999999);assert(!Open(file,bad));
  bad=good;bad[40*2048+2]=255;bad[40*2048+3]=127;assert(!Open(file,bad));
  bad=good;Put32(bad,40*2048+4,40);Put32(bad,40*2048+8,2048);bad[40*2048+12]=16;assert(!Open(file,bad));
  bad=good;Put32(bad,32*2048+24,0xffffffff);assert(!Open(file,bad));
  bad.resize(32*2048+20);assert(!Open(file,bad));
  if(argc==3&&std::string(argv[1])=="--fuzz"){
    const int iterations=std::clamp(std::stoi(argv[2]),1,10000);
    std::mt19937 random(0x46483444);
    for(int i=0;i<iterations;i++){
      bad=good;
      for(int field=0;field<8;field++){
        size_t offset=field<4?32*2048+20+random()%8:40*2048+random()%32;
        bad[offset]=static_cast<unsigned char>(random());
      }
      if(i%3==0)bad.resize(random()%bad.size());
      (void)Open(file,bad);
    }
    std::cout<<"PASS: "<<iterations<<" malformed disc parser corpus cases\n";
  }
  std::filesystem::remove(file);
  std::cout<<"PASS: native disc reader validates images and handles descriptor reads and truncation\n";
}
