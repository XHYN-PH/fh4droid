#include <dlfcn.h>
#include <jni.h>
#include <vulkan/vulkan.h>
#ifndef FH4DROID_DRIVER_PROBE_TEST
#include <rex/cvar.h>
#include <rex/ui/vulkan/android_gpu_driver.h>
#include <filesystem>
#endif
#include <unistd.h>

#include <algorithm>
#include <cmath>
#include <cctype>
#include <cstdlib>
#include <functional>
#include <regex>
#include <string>
#include <vector>

namespace {
std::string Read(JNIEnv* e, jstring s) {
  if (!s) return {};
  const char* p = e->GetStringUTFChars(s, nullptr);
  std::string r = p ? p : "";
  if (p) e->ReleaseStringUTFChars(s, p);
  return r;
}
std::string Quote(const std::string& s) {
  std::string r = "\"";
  for (unsigned char c : s) {
    if (c == '"' || c == '\\') r += '\\';
    if (c >= 32) r += char(c);
  }
  return r + '"';
}
std::string Error(const std::string& s) {
  return "{\"ok\":false,\"error\":" + Quote(s) + "}";
}
template <class T>
T Load(PFN_vkGetInstanceProcAddr get, VkInstance instance, const char* name) {
  return reinterpret_cast<T>(get(instance, name));
}

// Render a tiny offscreen target and read its pixels back. No game files or
// SDL.
std::string RenderTest(PFN_vkGetInstanceProcAddr get, VkInstance instance,
                       VkPhysicalDevice physical, VkDevice device,
                       uint32_t family, bool core13, bool dynamic_rendering = true, VkFormat test_format=VK_FORMAT_R8G8B8A8_UNORM) {
  auto gd = Load<PFN_vkGetDeviceProcAddr>(get, instance, "vkGetDeviceProcAddr");
  if (!gd) return "Device entry points unavailable.";
#define FN(name)                                               \
  auto name = reinterpret_cast<PFN_##name>(gd(device, #name)); \
  if (!name) return "Missing " #name "."
  FN(vkCreateImage);
  FN(vkDestroyImage);
  FN(vkGetImageMemoryRequirements);
  FN(vkAllocateMemory);
  FN(vkFreeMemory);
  FN(vkBindImageMemory);
  FN(vkCreateImageView);
  FN(vkDestroyImageView);
  FN(vkCreateBuffer);
  FN(vkDestroyBuffer);
  FN(vkGetBufferMemoryRequirements);
  FN(vkBindBufferMemory);
  FN(vkCreateCommandPool);
  FN(vkDestroyCommandPool);
  FN(vkAllocateCommandBuffers);
  FN(vkBeginCommandBuffer);
  FN(vkEndCommandBuffer);
  FN(vkCmdPipelineBarrier);
  FN(vkCmdCopyImageToBuffer);
  FN(vkCreateFence);
  FN(vkDestroyFence);
  FN(vkWaitForFences);
  FN(vkGetDeviceQueue);
  FN(vkQueueSubmit);
  FN(vkMapMemory);
  FN(vkUnmapMemory);
  FN(vkDeviceWaitIdle);
  FN(vkCreateRenderPass);
  FN(vkDestroyRenderPass);
  FN(vkCreateFramebuffer);
  FN(vkDestroyFramebuffer);
  FN(vkCmdBeginRenderPass);
  FN(vkCmdEndRenderPass);
#undef FN
  auto begin = reinterpret_cast<PFN_vkCmdBeginRendering>(
      gd(device, core13 ? "vkCmdBeginRendering" : "vkCmdBeginRenderingKHR"));
  auto end = reinterpret_cast<PFN_vkCmdEndRendering>(
      gd(device, core13 ? "vkCmdEndRendering" : "vkCmdEndRenderingKHR"));
  auto properties = Load<PFN_vkGetPhysicalDeviceMemoryProperties>(
      get, instance, "vkGetPhysicalDeviceMemoryProperties");
  if ((dynamic_rendering && (!begin || !end)) || !properties)
    return "Dynamic rendering or memory entry points unavailable.";
  VkImage image{};
  VkImageView view{};
  VkBuffer buffer{};
  VkDeviceMemory image_memory{}, buffer_memory{};
  VkCommandPool pool{};
  VkFence fence{};
  VkRenderPass pass{};
  VkFramebuffer framebuffer{};
  bool submitted = false, complete = false;
  struct Cleanup {
    std::function<void()> run;
    ~Cleanup() { run(); }
  } cleanup{[&] {
    // A timed-out queue is left for the isolated process to exit, never block
    // its UI.
    if (submitted && !complete) return;
    if (fence) vkDestroyFence(device, fence, nullptr);
    if (pool) vkDestroyCommandPool(device, pool, nullptr);
    if (framebuffer) vkDestroyFramebuffer(device, framebuffer, nullptr);
    if (pass) vkDestroyRenderPass(device, pass, nullptr);
    if (view) vkDestroyImageView(device, view, nullptr);
    if (image) vkDestroyImage(device, image, nullptr);
    if (buffer) vkDestroyBuffer(device, buffer, nullptr);
    if (image_memory) vkFreeMemory(device, image_memory, nullptr);
    if (buffer_memory) vkFreeMemory(device, buffer_memory, nullptr);
  }};
#define TRY(expr) \
  if ((expr) != VK_SUCCESS) return "Render check failed at " #expr "."
  VkPhysicalDeviceMemoryProperties memory{};
  properties(physical, &memory);
  auto allocate = [&](VkMemoryRequirements req, VkMemoryPropertyFlags flags,
                      VkDeviceMemory* out) {
    for (uint32_t i = 0; i < memory.memoryTypeCount; i++)
      if ((req.memoryTypeBits & (1u << i)) &&
          (memory.memoryTypes[i].propertyFlags & flags) == flags) {
        VkMemoryAllocateInfo a{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        a.allocationSize = req.size;
        a.memoryTypeIndex = i;
        return vkAllocateMemory(device, &a, nullptr, out);
      }
    return VK_ERROR_FEATURE_NOT_PRESENT;
  };
  VkImageCreateInfo ic{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
  ic.imageType = VK_IMAGE_TYPE_2D;
  ic.format = test_format;
  ic.extent = {16, 16, 1};
  ic.mipLevels = ic.arrayLayers = 1;
  ic.samples = VK_SAMPLE_COUNT_1_BIT;
  ic.tiling = VK_IMAGE_TILING_OPTIMAL;
  ic.usage =
      VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
  TRY(vkCreateImage(device, &ic, nullptr, &image));
  VkMemoryRequirements req{};
  vkGetImageMemoryRequirements(device, image, &req);
  TRY(allocate(req, 0, &image_memory));
  TRY(vkBindImageMemory(device, image, image_memory, 0));
  VkImageViewCreateInfo iv{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
  iv.image = image;
  iv.viewType = VK_IMAGE_VIEW_TYPE_2D;
  iv.format = ic.format;
  iv.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
  TRY(vkCreateImageView(device, &iv, nullptr, &view));
  VkBufferCreateInfo bc{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
  bc.size = 16 * 16 * (test_format==VK_FORMAT_R16G16B16A16_SFLOAT?8:4);
  bc.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
  TRY(vkCreateBuffer(device, &bc, nullptr, &buffer));
  vkGetBufferMemoryRequirements(device, buffer, &req);
  TRY(allocate(req,
               VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                   VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
               &buffer_memory));
  TRY(vkBindBufferMemory(device, buffer, buffer_memory, 0));
  VkCommandPoolCreateInfo pc{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
  pc.queueFamilyIndex = family;
  TRY(vkCreateCommandPool(device, &pc, nullptr, &pool));
  VkCommandBufferAllocateInfo ca{
      VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
  ca.commandPool = pool;
  ca.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
  ca.commandBufferCount = 1;
  VkCommandBuffer command{};
  TRY(vkAllocateCommandBuffers(device, &ca, &command));
  VkCommandBufferBeginInfo cb{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
  cb.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
  TRY(vkBeginCommandBuffer(command, &cb));
  VkImageMemoryBarrier barrier{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
  barrier.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
  barrier.newLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
  barrier.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
  barrier.srcQueueFamilyIndex = barrier.dstQueueFamilyIndex =
      VK_QUEUE_FAMILY_IGNORED;
  barrier.image = image;
  barrier.subresourceRange = iv.subresourceRange;
  vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                       VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, 0, 0,
                       nullptr, 0, nullptr, 1, &barrier);
  VkRenderingAttachmentInfo attachment{
      VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO};
  attachment.imageView = view;
  attachment.imageLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
  attachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
  attachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
  attachment.clearValue.color = {{.25f, .5f, .75f, 1.f}};
  VkRenderingInfo rendering{VK_STRUCTURE_TYPE_RENDERING_INFO};
  rendering.renderArea.extent = {16, 16};
  rendering.layerCount = 1;
  rendering.colorAttachmentCount = 1;
  rendering.pColorAttachments = &attachment;
  if (dynamic_rendering) {
    begin(command, &rendering); end(command);
  } else {
    VkAttachmentDescription description{};
    description.format=ic.format;description.samples=VK_SAMPLE_COUNT_1_BIT;
    description.loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR;description.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
    description.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE;description.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
    description.initialLayout=description.finalLayout=VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    VkAttachmentReference reference{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription subpass{};subpass.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;subpass.colorAttachmentCount=1;subpass.pColorAttachments=&reference;
    VkRenderPassCreateInfo rp{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};rp.attachmentCount=1;rp.pAttachments=&description;rp.subpassCount=1;rp.pSubpasses=&subpass;
    TRY(vkCreateRenderPass(device,&rp,nullptr,&pass));
    VkFramebufferCreateInfo fb{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};fb.renderPass=pass;fb.attachmentCount=1;fb.pAttachments=&view;fb.width=fb.height=16;fb.layers=1;
    TRY(vkCreateFramebuffer(device,&fb,nullptr,&framebuffer));
    VkRenderPassBeginInfo render{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};render.renderPass=pass;render.framebuffer=framebuffer;render.renderArea.extent={16,16};render.clearValueCount=1;render.pClearValues=&attachment.clearValue;
    vkCmdBeginRenderPass(command,&render,VK_SUBPASS_CONTENTS_INLINE);vkCmdEndRenderPass(command);
  }
  barrier.oldLayout = barrier.newLayout;
  barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
  barrier.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
  barrier.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
  vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                       VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0,
                       nullptr, 1, &barrier);
  VkBufferImageCopy copy{};
  copy.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
  copy.imageExtent = {16, 16, 1};
  vkCmdCopyImageToBuffer(command, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                         buffer, 1, &copy);
  VkMemoryBarrier host{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
  host.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
  host.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
  vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_TRANSFER_BIT,
                       VK_PIPELINE_STAGE_HOST_BIT, 0, 1, &host, 0, nullptr, 0,
                       nullptr);
  TRY(vkEndCommandBuffer(command));
  VkFenceCreateInfo fc{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
  TRY(vkCreateFence(device, &fc, nullptr, &fence));
  VkQueue queue{};
  vkGetDeviceQueue(device, family, 0, &queue);
  VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
  submit.commandBufferCount = 1;
  submit.pCommandBuffers = &command;
  TRY(vkQueueSubmit(queue, 1, &submit, fence));
  submitted = true;
  if (vkWaitForFences(device, 1, &fence, VK_TRUE, 2000000000ull) != VK_SUCCESS)
    return "Render check timed out or the driver stopped responding.";
  complete = true;
  void* pixels{};
  TRY(vkMapMemory(device, buffer_memory, 0, VK_WHOLE_SIZE, 0, &pixels));
  bool matches = true;
  const auto* p = static_cast<const unsigned char*>(pixels);
  for (int i = 0; i < 16 * 16; i++) {
    if(test_format==VK_FORMAT_R16G16B16A16_SFLOAT){
      const auto* half=reinterpret_cast<const uint16_t*>(p)+i*4;
      matches &= half[0]==0x3400&&half[1]==0x3800&&half[2]==0x3a00&&half[3]==0x3c00;
    }else if(test_format==VK_FORMAT_A2B10G10R10_UNORM_PACK32){
      const uint32_t packed=reinterpret_cast<const uint32_t*>(p)[i];
      matches &= std::abs(int(packed&1023)-256)<=1&&std::abs(int((packed>>10)&1023)-512)<=1&&std::abs(int((packed>>20)&1023)-767)<=1&&(packed>>30)==3;
    }else matches &= std::abs(int(p[i * 4]) - 64) <= 1 && std::abs(int(p[i * 4 + 1]) - 128) <= 1 && std::abs(int(p[i * 4 + 2]) - 191) <= 1 && p[i * 4 + 3] == 255;
  }
  vkUnmapMemory(device, buffer_memory);
#undef TRY
  return matches ? "" : "The rendered pixels did not match the test image.";
}

std::string Probe(void* loader, std::string* first_name = nullptr,
                  uint32_t max_api = VK_API_VERSION_1_3, bool force_legacy = false) {
  auto get = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
      dlsym(loader, "vkGetInstanceProcAddr"));
  if (!get) return Error("Missing Vulkan entry points.");
  auto create = Load<PFN_vkCreateInstance>(get, nullptr, "vkCreateInstance");
  auto version = Load<PFN_vkEnumerateInstanceVersion>(
      get, nullptr, "vkEnumerateInstanceVersion");
  uint32_t api = VK_API_VERSION_1_0;
  if (!create || !version || version(&api) != VK_SUCCESS ||
      api < VK_API_VERSION_1_1)
    return Error("This build requires Vulkan 1.1 and the listed graphics features.");
  VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
  app.pApplicationName = "FH4Droid driver test";
  app.apiVersion = std::min(api, max_api);
  VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
  ci.pApplicationInfo = &app;
  VkInstance instance{};
  VkResult status = create(&ci, nullptr, &instance);
  if (status != VK_SUCCESS)
    return Error("Vulkan instance creation failed: " + std::to_string(status));
  auto destroy =
      Load<PFN_vkDestroyInstance>(get, instance, "vkDestroyInstance");
  auto enumerate = Load<PFN_vkEnumeratePhysicalDevices>(
      get, instance, "vkEnumeratePhysicalDevices");
  auto props = Load<PFN_vkGetPhysicalDeviceProperties>(
      get, instance, "vkGetPhysicalDeviceProperties");
  auto props2 = Load<PFN_vkGetPhysicalDeviceProperties2>(
      get, instance, "vkGetPhysicalDeviceProperties2");
  auto features = Load<PFN_vkGetPhysicalDeviceFeatures2>(
      get, instance, "vkGetPhysicalDeviceFeatures2");
  auto formats = Load<PFN_vkGetPhysicalDeviceFormatProperties>(
      get, instance, "vkGetPhysicalDeviceFormatProperties");
  auto queues = Load<PFN_vkGetPhysicalDeviceQueueFamilyProperties>(
      get, instance, "vkGetPhysicalDeviceQueueFamilyProperties");
  auto extensions = Load<PFN_vkEnumerateDeviceExtensionProperties>(
      get, instance, "vkEnumerateDeviceExtensionProperties");
  auto create_device =
      Load<PFN_vkCreateDevice>(get, instance, "vkCreateDevice");
  auto destroy_device =
      Load<PFN_vkDestroyDevice>(get, instance, "vkDestroyDevice");
  if (!destroy || !enumerate || !props || !features || !formats || !queues ||
      !extensions || !create_device || !destroy_device) {
    if (destroy) destroy(instance, nullptr);
    return Error("The Vulkan driver is missing required functions.");
  }
  uint32_t count = 0;
  enumerate(instance, &count, nullptr);
  if (!count || count > 32) {
    destroy(instance, nullptr);
    return Error("No usable Vulkan GPU found.");
  }
  std::vector<VkPhysicalDevice> devices(count);
  if (enumerate(instance, &count, devices.data()) != VK_SUCCESS) {
    destroy(instance, nullptr);
    return Error("Could not enumerate Vulkan GPUs.");
  }
  std::string last = "No compatible GPU found.", result;
  for (auto device : devices) {
    VkPhysicalDeviceProperties p{};
    props(device, &p);
    VkPhysicalDeviceDriverProperties driver{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES};
    if (props2 && p.apiVersion >= VK_API_VERSION_1_2) {
      VkPhysicalDeviceProperties2 properties2{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2};
      properties2.pNext = &driver;
      props2(device, &properties2);
    }
    if (first_name && first_name->empty()) *first_name = p.deviceName;
    uint32_t ec=0;extensions(device,nullptr,&ec,nullptr);std::vector<VkExtensionProperties> ex(ec);
    if(extensions(device,nullptr,&ec,ex.data())!=VK_SUCCESS){last="Could not enumerate Vulkan device extensions.";continue;}
    const auto has=[&](const char* name){return std::any_of(ex.begin(),ex.end(),[&](const auto& e){return std::string(e.extensionName)==name;});};
    const bool core13=std::min(p.apiVersion,app.apiVersion)>=VK_API_VERSION_1_3;
    const bool dependencies=p.apiVersion>=VK_API_VERSION_1_2 || (has(VK_KHR_DEPTH_STENCIL_RESOLVE_EXTENSION_NAME)&&has(VK_KHR_CREATE_RENDERPASS_2_EXTENSION_NAME));
    const bool dynamic_supported=!force_legacy && (core13 || (has(VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME)&&dependencies));
    VkPhysicalDeviceDynamicRenderingFeatures f13{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES};
    VkPhysicalDeviceVulkan12Features f12{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES};
    f12.pNext = dynamic_supported ? &f13 : nullptr;
    VkPhysicalDeviceFeatures2 f{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
    f.pNext = p.apiVersion>=VK_API_VERSION_1_2 ? static_cast<void*>(&f12) : dynamic_supported ? static_cast<void*>(&f13) : nullptr;
    features(device, &f);
    if (p.apiVersion < VK_API_VERSION_1_1 ||
        !f.features.independentBlend || !f.features.fragmentStoresAndAtomics ||
        !f.features.vertexPipelineStoresAndAtomics) {
      last = std::string(p.deviceName) +
             ": missing Vulkan 1.1 or required blend/atomic "
             "features.";
      continue;
    }
    VkFormatProperties depth{}, depth32{}, output{};
    formats(device, VK_FORMAT_D24_UNORM_S8_UINT, &depth);
    formats(device, VK_FORMAT_D32_SFLOAT_S8_UINT, &depth32);
    formats(device, VK_FORMAT_A2B10G10R10_UNORM_PACK32, &output);
    const auto depth_bits = VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT |
                            VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT;
    const auto output_bits = VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT |
                             VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT |
                             VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT;
    if (((depth.optimalTilingFeatures & depth_bits) != depth_bits &&
         (depth32.optimalTilingFeatures & depth_bits) != depth_bits) ||
        (output.optimalTilingFeatures & output_bits) != output_bits) {
      last = std::string(p.deviceName) +
             ": required depth/output formats are unavailable.";
      continue;
    }
    const bool dynamic=dynamic_supported && f13.dynamicRendering;
    if(!has(VK_KHR_SWAPCHAIN_EXTENSION_NAME)){last="The driver does not support Vulkan swapchains.";continue;}
    uint32_t qc = 0;
    queues(device, &qc, nullptr);
    std::vector<VkQueueFamilyProperties> qp(qc);
    queues(device, &qc, qp.data());
    uint32_t family = qc;
    for (uint32_t i = 0; i < qc; ++i)
      if (qp[i].queueCount &&
          (qp[i].queueFlags & (VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT)) ==
              (VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT)) {
        family = i;
        break;
      }
    if (family == qc) {
      last = "The driver has no graphics/compute queue.";
      continue;
    }
    float priority = 1;
    VkDeviceQueueCreateInfo qi{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    qi.queueFamilyIndex = family;
    qi.queueCount = 1;
    qi.pQueuePriorities = &priority;
    VkDeviceCreateInfo di{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    di.queueCreateInfoCount = 1;
    di.pQueueCreateInfos = &qi;
    // Exercise the features queried above without loading SDL or the game.
    VkPhysicalDeviceFeatures enabled{};
    enabled.independentBlend = VK_TRUE;
    enabled.fragmentStoresAndAtomics = VK_TRUE;
    enabled.vertexPipelineStoresAndAtomics = VK_TRUE;
    VkPhysicalDeviceDynamicRenderingFeatures enabled13{
        VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES};
    enabled13.dynamicRendering = VK_TRUE;
    di.pEnabledFeatures = &enabled;
    di.pNext = dynamic ? &enabled13 : nullptr;
    std::vector<const char*> required{VK_KHR_SWAPCHAIN_EXTENSION_NAME};
    if(dynamic && !core13){
      required.push_back(VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME);
      if(p.apiVersion<VK_API_VERSION_1_2){required.push_back(VK_KHR_DEPTH_STENCIL_RESOLVE_EXTENSION_NAME);required.push_back(VK_KHR_CREATE_RENDERPASS_2_EXTENSION_NAME);}
    }
    di.enabledExtensionCount=uint32_t(required.size());di.ppEnabledExtensionNames=required.data();
    VkDevice logical{};
    status = create_device(device, &di, nullptr, &logical);
    if (status != VK_SUCCESS) {
      last = "Vulkan device creation failed: " + std::to_string(status);
      continue;
    }
    const auto render_error =
        RenderTest(get, instance, device, logical, family, core13, dynamic);
    // A hung queue is reclaimed when DriverProbeActivity exits its isolated
    // process.
    if (!render_error.empty()) return Error(render_error);
    for(VkFormat format:{VK_FORMAT_A2B10G10R10_UNORM_PACK32,VK_FORMAT_R16G16B16A16_SFLOAT}){
      const auto error=RenderTest(get,instance,device,logical,family,core13,dynamic,format);
      if(!error.empty())return Error("HDR/10-bit readback: "+error);
    }
    destroy_device(logical, nullptr);
    std::string gpu_family="other";std::string gpu_name=p.deviceName;
    std::transform(gpu_name.begin(),gpu_name.end(),gpu_name.begin(),[](unsigned char c){return char(std::tolower(c));});
    if(gpu_name.find("xclipse")!=std::string::npos||p.vendorID==0x144d)gpu_family="xclipse";
    else if(p.vendorID==0x13b5||gpu_name.find("mali")!=std::string::npos||gpu_name.find("immortalis")!=std::string::npos)gpu_family="mali";
    else if(p.vendorID==0x5143)gpu_family="adreno";
    result = "{\"ok\":true,\"gpu_family\":"+Quote(gpu_family)+",\"gpu\":" + Quote(p.deviceName) +
             ",\"vendor_id\":" + std::to_string(p.vendorID) +
             ",\"driver_id\":" + std::to_string(driver.driverID) +
             ",\"driver_name\":" + Quote(driver.driverName) +
             ",\"driver_info\":" + Quote(driver.driverInfo) +
             ",\"turnip_debug\":" + Quote(std::getenv("TU_DEBUG") ? std::getenv("TU_DEBUG") : "") +
             ",\"ir3_shader_debug\":" + Quote(std::getenv("IR3_SHADER_DEBUG") ? std::getenv("IR3_SHADER_DEBUG") : "") +
             ",\"api\":" + std::to_string(p.apiVersion) +
             ",\"driver\":" + std::to_string(p.driverVersion) +
             ",\"dynamic_rendering\":" + (dynamic ? "true" : "false") +
             ",\"renderer\":" + Quote(dynamic ? "native" : "compatibility") +
             ",\"descriptor_indexing\":" +
             (f12.runtimeDescriptorArray && f12.descriptorBindingPartiallyBound
                  ? "true"
                  : "false") +
             ",\"checks\":[{\"name\":\"Required graphics "
             "features\",\"ok\":true,\"detail\":\"Blend and required atomics\"},"
             "{\"name\":\"Colour and depth "
             "formats\",\"ok\":true,\"detail\":\"Required sampled, storage and "
             "depth formats\"},"
             "{\"name\":\"Render and readback\",\"ok\":true,\"detail\":\""
             "RGBA8, 10-bit and float16 clear/readback passed; materials and full gameplay still need testing\"}]}";
    break;
  }
  destroy(instance, nullptr);
  return result.empty() ? Error(last) : result;
}
}  // namespace


#ifndef FH4DROID_DRIVER_PROBE_TEST
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

#endif
