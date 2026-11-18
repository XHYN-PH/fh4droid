// Load the actual generated facade libraries and register every translated
// address through the SDK dispatcher before any guest code is allowed to run.
#include <rex/runtime.h>
#include <rex/kernel/init.h>
#include <rex/image_info.h>
#include <rex/system/kernel_state.h>
#include <rex/system/function_dispatcher.h>
#include <dlfcn.h>
#include <cstddef>
#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <vector>
static_assert(offsetof(rex::PPCImageInfo, register_modules) == 32);
extern const rex::PPCImageInfo PPCImageConfig;
int main(int argc,char** argv) {
  if(argc!=5)return 2;
  try {
    const std::filesystem::path state=argv[2];
    rex::Runtime runtime(argv[1],state/"user",state/"update",state/"cache");
    if(!PPCImageConfig.register_modules)throw std::runtime_error("entrypoint module callback is null");
    if(runtime.Setup(PPCImageConfig,rex::RuntimeConfig{.kernel_init=rex::kernel::InitializeKernel,.tool_mode=true})!=0)throw std::runtime_error("runtime setup");
    auto* kernel=runtime.kernel_state();
    // ReXApp calls this after Runtime::Setup and before launching the guest.
    PPCImageConfig.register_modules(kernel);
    for(const auto* name:{"game:\\XMediaFacade_default.xex","game:\\SpeechFacade_default.xex"}) {
      const auto found=kernel->FindRecompiledModule(name);
      if(!found||found->shared_lib_name.empty())throw std::runtime_error("facade missing from the runtime registry");
    }
    auto* dispatcher=runtime.function_dispatcher();
    std::vector<void*> libraries;
    size_t count=0;
    for(int i=3;i<5;i++) {
      void* library=dlopen(argv[i],RTLD_NOW|RTLD_LOCAL);
      if(!library)throw std::runtime_error(dlerror());
      libraries.push_back(library);
      auto get=reinterpret_cast<const rex::PPCImageInfo*(*)()>(dlsym(library,"ReXModule_GetImageInfo"));
      auto reg=reinterpret_cast<rex::runtime::FunctionDispatcher::RegisterFn>(dlsym(library,"ReXModule_Register"));
      if(!get||!reg)throw std::runtime_error("missing facade exports");
      const auto& info=*get();
      if(!dispatcher->InitializeFunctionTable(info.code_base,info.code_size,info.image_base,info.image_size))throw std::runtime_error("facade table layout");
      if(dispatcher->GetFunction(info.code_base))throw std::runtime_error("unregistered table was not empty");
      dispatcher->RegisterModule(argv[i],info.code_base,reg);
      for(auto* mapping=info.func_mappings;mapping->guest;mapping++,count++) {
        if(dispatcher->GetFunction(mapping->guest)!=mapping->host)throw std::runtime_error("generated function mapping mismatch");
      }
    }
    if(!dispatcher->GetFunction(0x88050800)||!dispatcher->GetFunction(0x88050388))throw std::runtime_error("reported startup addresses remain unregistered");
    std::cout<<"PASS: generated entrypoint callback, both facade registry paths, layouts, exports, "<<count<<" function mappings and reported startup targets\n";
    // Keep library code mapped until the runtime dispatcher is destroyed.
    return 0;
  }catch(const std::exception& error){std::cerr<<error.what()<<'\n';return 1;}
}
