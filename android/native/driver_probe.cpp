#include <jni.h>
#include <dlfcn.h>
#include <vulkan/vulkan.h>
#include <rex/ui/vulkan/android_gpu_driver.h>
#include <rex/cvar.h>
#include <filesystem>
#include <algorithm>
#include <string>
#include <vector>
#include <regex>
#include <unistd.h>

namespace {
std::string Read(JNIEnv* e,jstring s){if(!s)return {};const char* p=e->GetStringUTFChars(s,nullptr);std::string r=p?p:"";if(p)e->ReleaseStringUTFChars(s,p);return r;}
std::string Quote(const std::string& s){std::string r="\"";for(unsigned char c:s){if(c=='"'||c=='\\')r+='\\';if(c>=32)r+=char(c);}return r+'"';}
std::string Error(const std::string& s){return "{\"ok\":false,\"error\":"+Quote(s)+"}";}
template<class T>T Load(PFN_vkGetInstanceProcAddr get,VkInstance instance,const char* name){return reinterpret_cast<T>(get(instance,name));}

std::string Probe(void* loader,std::string* first_name=nullptr){
    auto get=reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(loader,"vkGetInstanceProcAddr"));
    if(!get)return Error("Missing Vulkan entry points.");
    auto create=Load<PFN_vkCreateInstance>(get,nullptr,"vkCreateInstance");
    auto version=Load<PFN_vkEnumerateInstanceVersion>(get,nullptr,"vkEnumerateInstanceVersion");
    uint32_t api=VK_API_VERSION_1_0;
    if(!create||!version||version(&api)!=VK_SUCCESS||api<VK_API_VERSION_1_3)return Error("This build requires Vulkan 1.3.");
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};app.pApplicationName="FH4Droid driver test";app.apiVersion=VK_API_VERSION_1_3;
    VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};ci.pApplicationInfo=&app;
    VkInstance instance{};VkResult status=create(&ci,nullptr,&instance);
    if(status!=VK_SUCCESS)return Error("Vulkan instance creation failed: "+std::to_string(status));
    auto destroy=Load<PFN_vkDestroyInstance>(get,instance,"vkDestroyInstance");
    auto enumerate=Load<PFN_vkEnumeratePhysicalDevices>(get,instance,"vkEnumeratePhysicalDevices");
    auto props=Load<PFN_vkGetPhysicalDeviceProperties>(get,instance,"vkGetPhysicalDeviceProperties");
    auto features=Load<PFN_vkGetPhysicalDeviceFeatures2>(get,instance,"vkGetPhysicalDeviceFeatures2");
    auto formats=Load<PFN_vkGetPhysicalDeviceFormatProperties>(get,instance,"vkGetPhysicalDeviceFormatProperties");
    auto queues=Load<PFN_vkGetPhysicalDeviceQueueFamilyProperties>(get,instance,"vkGetPhysicalDeviceQueueFamilyProperties");
    auto extensions=Load<PFN_vkEnumerateDeviceExtensionProperties>(get,instance,"vkEnumerateDeviceExtensionProperties");
    auto create_device=Load<PFN_vkCreateDevice>(get,instance,"vkCreateDevice");
    auto destroy_device=Load<PFN_vkDestroyDevice>(get,instance,"vkDestroyDevice");
    if(!destroy||!enumerate||!props||!features||!formats||!queues||!extensions||!create_device||!destroy_device){
        if(destroy)destroy(instance,nullptr);return Error("The Vulkan driver is missing required functions.");}
    uint32_t count=0;enumerate(instance,&count,nullptr);
    if(!count||count>32){destroy(instance,nullptr);return Error("No usable Vulkan GPU found.");}
    std::vector<VkPhysicalDevice> devices(count);if(enumerate(instance,&count,devices.data())!=VK_SUCCESS){destroy(instance,nullptr);return Error("Could not enumerate Vulkan GPUs.");}
    std::string last="No compatible GPU found.",result;
    for(auto device:devices){
        VkPhysicalDeviceProperties p{};props(device,&p);if(first_name&&first_name->empty())*first_name=p.deviceName;
        VkPhysicalDeviceVulkan13Features f13{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES};
        VkPhysicalDeviceVulkan12Features f12{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES};f12.pNext=&f13;
        VkPhysicalDeviceFeatures2 f{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};f.pNext=&f12;features(device,&f);
        if(p.apiVersion<VK_API_VERSION_1_3||!f13.dynamicRendering||!f.features.independentBlend||
           !f.features.fragmentStoresAndAtomics||!f.features.vertexPipelineStoresAndAtomics){
            last=std::string(p.deviceName)+": missing Vulkan 1.3, dynamic rendering or required blend/atomic features.";continue;}
        VkFormatProperties depth{},depth32{},output{};formats(device,VK_FORMAT_D24_UNORM_S8_UINT,&depth);
        formats(device,VK_FORMAT_D32_SFLOAT_S8_UINT,&depth32);formats(device,VK_FORMAT_A2B10G10R10_UNORM_PACK32,&output);
        const auto depth_bits=VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT|VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT;
        const auto output_bits=VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT|VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT|VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT;
        if(((depth.optimalTilingFeatures&depth_bits)!=depth_bits&&(depth32.optimalTilingFeatures&depth_bits)!=depth_bits)||
           (output.optimalTilingFeatures&output_bits)!=output_bits){last=std::string(p.deviceName)+": required depth/output formats are unavailable.";continue;}
        uint32_t ec=0;extensions(device,nullptr,&ec,nullptr);std::vector<VkExtensionProperties> ex(ec);extensions(device,nullptr,&ec,ex.data());
        bool swapchain=false;for(const auto& e:ex)if(std::string(e.extensionName)==VK_KHR_SWAPCHAIN_EXTENSION_NAME)swapchain=true;
        if(!swapchain){last="The driver does not support Vulkan swapchains.";continue;}
        uint32_t qc=0;queues(device,&qc,nullptr);std::vector<VkQueueFamilyProperties> qp(qc);queues(device,&qc,qp.data());uint32_t family=qc;
        for(uint32_t i=0;i<qc;++i)if(qp[i].queueCount&&(qp[i].queueFlags&(VK_QUEUE_GRAPHICS_BIT|VK_QUEUE_COMPUTE_BIT))==(VK_QUEUE_GRAPHICS_BIT|VK_QUEUE_COMPUTE_BIT)){family=i;break;}
        if(family==qc){last="The driver has no graphics/compute queue.";continue;}
        float priority=1;VkDeviceQueueCreateInfo qi{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};qi.queueFamilyIndex=family;qi.queueCount=1;qi.pQueuePriorities=&priority;
        VkDeviceCreateInfo di{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};di.queueCreateInfoCount=1;di.pQueueCreateInfos=&qi;
        // Exercise the features queried above without loading SDL or the game.
        VkPhysicalDeviceFeatures enabled{};enabled.independentBlend=VK_TRUE;enabled.fragmentStoresAndAtomics=VK_TRUE;enabled.vertexPipelineStoresAndAtomics=VK_TRUE;
        VkPhysicalDeviceVulkan13Features enabled13{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES};enabled13.dynamicRendering=VK_TRUE;
        di.pEnabledFeatures=&enabled;di.pNext=&enabled13;const char* extension=VK_KHR_SWAPCHAIN_EXTENSION_NAME;di.enabledExtensionCount=1;di.ppEnabledExtensionNames=&extension;
        VkDevice logical{};status=create_device(device,&di,nullptr,&logical);
        if(status!=VK_SUCCESS){last="Vulkan device creation failed: "+std::to_string(status);continue;}
        destroy_device(logical,nullptr);
        result="{\"ok\":true,\"gpu\":"+Quote(p.deviceName)+",\"api\":"+std::to_string(p.apiVersion)+",\"driver\":"+std::to_string(p.driverVersion)+
            ",\"descriptor_indexing\":"+(f12.runtimeDescriptorArray&&f12.descriptorBindingPartiallyBound?"true":"false")+"}";break;
    }
    destroy(instance,nullptr);return result.empty()?Error(last):result;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_studio_deimos_pinyonshift_DriverProbeActivity_nativeProbe(JNIEnv* env,jclass,jstring native_value,jstring root_value,jstring internal_value,jstring selected_value,jstring environment_value,jboolean no_preamble){
    rex::cvar::SetFlagByName("android_gpu_driver_env",Read(env,environment_value));
    rex::cvar::SetFlagByName("android_gpu_driver_no_preamble",no_preamble?"true":"false");
    const auto root=std::filesystem::path(Read(env,root_value));
    std::string selection=Read(env,selected_value);
    void* loader=dlopen("/system/lib64/libvulkan.so",RTLD_NOW|RTLD_LOCAL);
    if(!loader)return env->NewStringUTF(Error("The system Vulkan loader could not be opened.").c_str());
    if(selection=="auto"){
        std::string name;Probe(loader,&name);
        selection=std::regex_search(name,std::regex("Adreno[^0-9]*[78][0-9]{2}"))&&
            std::filesystem::is_directory(root/"turnip-gen8-v37")?"turnip-gen8-v37":"";
    }
    if(!selection.empty()){
        dlclose(loader);
        loader=rex::ui::vulkan::OpenAndroidCustomVulkanDriver(root,
            Read(env,internal_value),Read(env,native_value),selection);
        if(!loader)return env->NewStringUTF(Error("The selected custom driver could not be loaded.").c_str());
    }
    std::string report=Probe(loader);
    if(!report.empty()&&report.back()=='}'){report.pop_back();report+=",\"effective_driver\":"+Quote(selection)+"}";}
    // A fresh process is killed after reporting; do not unload live driver hooks.
    return env->NewStringUTF(report.c_str());
}
