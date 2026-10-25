// Host check of the actual isolated driver's offscreen render/readback path.
#define FH4DROID_DRIVER_PROBE_TEST
#include <iostream>

#include "../../android/native/driver_probe.cpp"
int main() {
  void* loader = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
  if (!loader) return 1;
  for (uint32_t version : {VK_API_VERSION_1_2, VK_API_VERSION_1_3}) {
    const auto result = Probe(loader, nullptr, version);
    std::cout << "API " << VK_VERSION_MAJOR(version) << '.'
              << VK_VERSION_MINOR(version) << ": " << result << std::endl;
    if (result.find("\"ok\":true") == std::string::npos) return 1;
  }
  dlclose(loader);
  std::cout << "PASS: Vulkan 1.2 extension and 1.3 core render/readback\n";
}
