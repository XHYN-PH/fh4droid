// Runs the shipped SPIR-V, not a duplicate of its decode arithmetic.
// Compile with the pinned Vulkan headers and link a Vulkan loader. A software
// ICD is sufficient; this checks correctness, never phone performance.
#include <vulkan/vulkan.h>
#include <rex/graphics/fh1_raster_scale.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <vector>

#include "src/graphics/shaders/vulkan_spirv/fh1_native_resolve_memory_color_cs.h"
#include "src/graphics/shaders/vulkan_spirv/fh1_native_resolve_memory_color_ms_cs.h"
#include "src/graphics/shaders/vulkan_spirv/fh1_native_resolve_memory_depth_cs.h"
#include "src/graphics/shaders/vulkan_spirv/fh1_native_transfer_words_color_cs.h"
#include "src/graphics/shaders/vulkan_spirv/fh1_native_transfer_words_depth_cs.h"
#define OK(call)                                            \
  do {                                                      \
    auto vk_status_ = (call);                               \
    if (vk_status_ != VK_SUCCESS)                           \
      throw std::runtime_error(#call + std::string(": ") +  \
                               std::to_string(vk_status_)); \
  } while (0)
namespace {
uint32_t Tiled(uint32_t x, uint32_t y, uint32_t pitch) {
  pitch = (pitch + 31) & ~31u;
  uint32_t macro = ((x >> 5) + (y >> 5) * (pitch >> 5)) << 9;
  uint32_t micro = ((x & 7) + ((y & 14) << 2)) << 2;
  uint32_t offset =
      macro + ((micro & ~15u) << 1) + (micro & 15) + ((y & 1) << 4);
  return ((offset & ~511u) << 3) + ((y & 16) << 7) + ((offset & 448) << 2) +
         (((((y & 8) >> 2) + (x >> 3)) & 3) << 6) + (offset & 63);
}
struct Gpu {
  VkInstance instance{};
  VkPhysicalDevice physical{};
  VkDevice device{};
  VkQueue queue{};
  uint32_t family{};
  VkCommandPool pool{};
  VkCommandBuffer command{};
  VkDescriptorSetLayout layout{};
  VkPipelineLayout pipeline_layout{};
  VkDescriptorPool descriptors{};
  VkDescriptorSet set{};
  VkBuffer buffer{}, upload{};
  VkDeviceMemory buffer_memory{}, upload_memory{};
  void *mapped{}, *input{};
  static constexpr size_t bytes = 1 << 20;
  uint32_t Memory(uint32_t bits, VkMemoryPropertyFlags flags) {
    VkPhysicalDeviceMemoryProperties p{};
    vkGetPhysicalDeviceMemoryProperties(physical, &p);
    for (uint32_t i = 0; i < p.memoryTypeCount; i++)
      if ((bits & (1u << i)) &&
          (p.memoryTypes[i].propertyFlags & flags) == flags)
        return i;
    throw std::runtime_error("memory type");
  }
  void Buffer(VkBufferUsageFlags usage, VkBuffer& b, VkDeviceMemory& memory,
              void** data) {
    VkBufferCreateInfo c{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    c.size = bytes;
    c.usage = usage;
    OK(vkCreateBuffer(device, &c, nullptr, &b));
    VkMemoryRequirements r{};
    vkGetBufferMemoryRequirements(device, b, &r);
    VkMemoryAllocateInfo a{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    a.allocationSize = r.size;
    a.memoryTypeIndex =
        Memory(r.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                                     VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    OK(vkAllocateMemory(device, &a, nullptr, &memory));
    OK(vkBindBufferMemory(device, b, memory, 0));
    OK(vkMapMemory(device, memory, 0, VK_WHOLE_SIZE, 0, data));
  }
  Gpu() {
    VkApplicationInfo a{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    a.apiVersion = VK_API_VERSION_1_1;
    VkInstanceCreateInfo c{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    c.pApplicationInfo = &a;
    OK(vkCreateInstance(&c, nullptr, &instance));
    uint32_t n = 1;
    OK(vkEnumeratePhysicalDevices(instance, &n, &physical));
    VkPhysicalDeviceProperties p{};
    vkGetPhysicalDeviceProperties(physical, &p);
    std::cout << "GPU: " << p.deviceName << std::endl;
    uint32_t count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(physical, &count, nullptr);
    std::vector<VkQueueFamilyProperties> q(count);
    vkGetPhysicalDeviceQueueFamilyProperties(physical, &count, q.data());
    for (; family < count; family++)
      if (q[family].queueFlags & VK_QUEUE_COMPUTE_BIT) break;
    float priority = 1;
    VkDeviceQueueCreateInfo qi{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    qi.queueFamilyIndex = family;
    qi.queueCount = 1;
    qi.pQueuePriorities = &priority;
    VkDeviceCreateInfo di{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    di.queueCreateInfoCount = 1;
    di.pQueueCreateInfos = &qi;
    OK(vkCreateDevice(physical, &di, nullptr, &device));
    vkGetDeviceQueue(device, family, 0, &queue);
    VkCommandPoolCreateInfo pc{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
    pc.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    pc.queueFamilyIndex = family;
    OK(vkCreateCommandPool(device, &pc, nullptr, &pool));
    VkCommandBufferAllocateInfo ca{
        VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    ca.commandPool = pool;
    ca.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    ca.commandBufferCount = 1;
    OK(vkAllocateCommandBuffers(device, &ca, &command));
    VkDescriptorSetLayoutBinding bindings[] = {
        {16, VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, 1, VK_SHADER_STAGE_COMPUTE_BIT,
         nullptr},
        {17, VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, 1, VK_SHADER_STAGE_COMPUTE_BIT,
         nullptr},
        {32, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT,
         nullptr}};
    VkDescriptorSetLayoutCreateInfo lc{
        VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
    lc.bindingCount = 3;
    lc.pBindings = bindings;
    OK(vkCreateDescriptorSetLayout(device, &lc, nullptr, &layout));
    VkPushConstantRange range{VK_SHADER_STAGE_COMPUTE_BIT, 0, 32};
    VkPipelineLayoutCreateInfo pl{
        VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
    pl.setLayoutCount = 1;
    pl.pSetLayouts = &layout;
    pl.pushConstantRangeCount = 1;
    pl.pPushConstantRanges = &range;
    OK(vkCreatePipelineLayout(device, &pl, nullptr, &pipeline_layout));
    VkDescriptorPoolSize sizes[] = {{VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, 2},
                                    {VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1}};
    VkDescriptorPoolCreateInfo dp{
        VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
    dp.maxSets = 1;
    dp.poolSizeCount = 2;
    dp.pPoolSizes = sizes;
    OK(vkCreateDescriptorPool(device, &dp, nullptr, &descriptors));
    VkDescriptorSetAllocateInfo ds{
        VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
    ds.descriptorPool = descriptors;
    ds.descriptorSetCount = 1;
    ds.pSetLayouts = &layout;
    OK(vkAllocateDescriptorSets(device, &ds, &set));
    Buffer(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, buffer, buffer_memory, &mapped);
    Buffer(VK_BUFFER_USAGE_TRANSFER_SRC_BIT, upload, upload_memory, &input);
    VkDescriptorBufferInfo bi{buffer, 0, bytes};
    VkWriteDescriptorSet w{VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
    w.dstSet = set;
    w.dstBinding = 32;
    w.descriptorCount = 1;
    w.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
    w.pBufferInfo = &bi;
    vkUpdateDescriptorSets(device, 1, &w, 0, nullptr);
  }
  ~Gpu() {
    if (!device) return;
    vkDeviceWaitIdle(device);
    vkDestroyBuffer(device, upload, nullptr);
    vkDestroyBuffer(device, buffer, nullptr);
    vkFreeMemory(device, upload_memory, nullptr);
    vkFreeMemory(device, buffer_memory, nullptr);
    vkDestroyDescriptorPool(device, descriptors, nullptr);
    vkDestroyPipelineLayout(device, pipeline_layout, nullptr);
    vkDestroyDescriptorSetLayout(device, layout, nullptr);
    vkDestroyCommandPool(device, pool, nullptr);
    vkDestroyDevice(device, nullptr);
    vkDestroyInstance(instance, nullptr);
  }
  void Begin() {
    OK(vkResetCommandBuffer(command, 0));
    VkCommandBufferBeginInfo b{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    OK(vkBeginCommandBuffer(command, &b));
  }
  void Submit() {
    OK(vkEndCommandBuffer(command));
    VkSubmitInfo s{VK_STRUCTURE_TYPE_SUBMIT_INFO};
    s.commandBufferCount = 1;
    s.pCommandBuffers = &command;
    VkFenceCreateInfo fc{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    VkFence fence{};
    OK(vkCreateFence(device, &fc, nullptr, &fence));
    OK(vkQueueSubmit(queue, 1, &s, fence));
    OK(vkWaitForFences(device, 1, &fence, VK_TRUE, 10000000000ull));
    vkDestroyFence(device, fence, nullptr);
  }
  VkPipeline Pipeline(const uint32_t* code, size_t size,
                      const std::array<uint32_t, 5>* spec = nullptr) {
    VkShaderModuleCreateInfo m{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
    m.codeSize = size;
    m.pCode = code;
    VkShaderModule module{};
    OK(vkCreateShaderModule(device, &m, nullptr, &module));
    std::array<VkSpecializationMapEntry, 5> entries{};
    for (uint32_t i = 0; i < 5; i++) entries[i] = {i, i * 4, 4};
    VkSpecializationInfo si{5, entries.data(), 20,
                            spec ? spec->data() : nullptr};
    VkComputePipelineCreateInfo p{
        VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};
    p.stage = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
    p.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
    p.stage.module = module;
    p.stage.pName = "main";
    p.stage.pSpecializationInfo = spec ? &si : nullptr;
    p.layout = pipeline_layout;
    VkPipeline result{};
    OK(vkCreateComputePipelines(device, VK_NULL_HANDLE, 1, &p, nullptr,
                                &result));
    vkDestroyShaderModule(device, module, nullptr);
    return result;
  }
  void Dispatch(VkPipeline pipeline, const uint32_t* constants, uint32_t w,
                uint32_t h, uint32_t group) {
    std::memset(mapped, 0xcc, bytes);
    Begin();
    vkCmdBindPipeline(command, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
    vkCmdBindDescriptorSets(command, VK_PIPELINE_BIND_POINT_COMPUTE,
                            pipeline_layout, 0, 1, &set, 0, nullptr);
    vkCmdPushConstants(command, pipeline_layout, VK_SHADER_STAGE_COMPUTE_BIT, 0,
                       32, constants);
    vkCmdDispatch(command, (w + group - 1) / group, (h + group - 1) / group, 1);
    VkMemoryBarrier barrier{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
    barrier.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
    barrier.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                         VK_PIPELINE_STAGE_HOST_BIT, 0, 1, &barrier, 0, nullptr,
                         0, nullptr);
    Submit();
  }
};
struct Source {
  Gpu& gpu;
  VkImage image{};
  VkDeviceMemory memory{};
  VkImageView view{}, stencil{};
  uint32_t width, height;
  bool depth, uniform;
  uint32_t samples;
  Source(Gpu& g, uint32_t w, uint32_t h, bool d, uint32_t sample_count=1,bool solid=false)
      : gpu(g), width(w), height(h), depth(d), uniform(solid), samples(sample_count) {
    VkImageCreateInfo c{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
    c.imageType = VK_IMAGE_TYPE_2D;
    c.format = d ? VK_FORMAT_D32_SFLOAT_S8_UINT : VK_FORMAT_R8G8B8A8_UNORM;
    c.extent = {w, h, 1};
    c.mipLevels = c.arrayLayers = 1;
    c.samples = VkSampleCountFlagBits(samples);
    c.tiling = VK_IMAGE_TILING_OPTIMAL;
    c.usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | (d ? 0 : VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
    OK(vkCreateImage(g.device, &c, nullptr, &image));
    VkMemoryRequirements r{};
    vkGetImageMemoryRequirements(g.device, image, &r);
    VkMemoryAllocateInfo a{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    a.allocationSize = r.size;
    a.memoryTypeIndex = g.Memory(r.memoryTypeBits, 0);
    OK(vkAllocateMemory(g.device, &a, nullptr, &memory));
    OK(vkBindImageMemory(g.device, image, memory, 0));
    auto makeView = [&](VkImageAspectFlags aspect, VkImageView& out) {
      VkImageViewCreateInfo v{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
      v.image = image;
      v.viewType = VK_IMAGE_VIEW_TYPE_2D;
      v.format = c.format;
      v.subresourceRange = {aspect, 0, 1, 0, 1};
      OK(vkCreateImageView(g.device, &v, nullptr, &out));
    };
    makeView(d ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT, view);
    if (d) makeView(VK_IMAGE_ASPECT_STENCIL_BIT, stencil);
    g.Begin();
    VkImageMemoryBarrier b{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    b.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    b.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    b.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    b.srcQueueFamilyIndex = b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    b.image = image;
    b.subresourceRange = {d ? VkImageAspectFlags(VK_IMAGE_ASPECT_DEPTH_BIT |
                                                 VK_IMAGE_ASPECT_STENCIL_BIT)
                            : VkImageAspectFlags(VK_IMAGE_ASPECT_COLOR_BIT),
                          0, 1, 0, 1};
    vkCmdPipelineBarrier(g.command, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                         VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0,
                         nullptr, 1, &b);
    if (d) {
      VkClearDepthStencilValue clear{.5f, 0xa5};
      vkCmdClearDepthStencilImage(g.command, image, b.newLayout, &clear, 1,
                                  &b.subresourceRange);
    } else if(uniform) {
      VkClearColorValue clear{{.25f,.5f,.75f,1}};
      vkCmdClearColorImage(g.command,image,b.newLayout,&clear,1,&b.subresourceRange);
    } else {
      auto* p = static_cast<uint32_t*>(g.input);
      for (uint32_t y = 0; y < h; y++)
        for (uint32_t x = 0; x < w; x++) p[y * w + x] = Pattern(x, y);
      VkBufferImageCopy copy{};
      copy.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
      copy.imageExtent = {w, h, 1};
      vkCmdCopyBufferToImage(g.command, g.upload, image, b.newLayout, 1, &copy);
    }
    b.oldLayout = b.newLayout;
    b.newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    b.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    b.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
    vkCmdPipelineBarrier(g.command, VK_PIPELINE_STAGE_TRANSFER_BIT,
                         VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 0, nullptr, 0,
                         nullptr, 1, &b);
    g.Submit();
    VkDescriptorImageInfo infos[2] = {
        {VK_NULL_HANDLE, view, b.newLayout},
        {VK_NULL_HANDLE, d ? stencil : view, b.newLayout}};
    VkWriteDescriptorSet writes[2]{};
    for (uint32_t i = 0; i < 2; i++) {
      writes[i] = {VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
      writes[i].dstSet = g.set;
      writes[i].dstBinding = 16 + i;
      writes[i].descriptorType = VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE;
      writes[i].descriptorCount = 1;
      writes[i].pImageInfo = &infos[i];
    }
    vkUpdateDescriptorSets(g.device, 2, writes, 0, nullptr);
  }
  // The production executor clears an attachment after each band's resolve.
  // Exercise the same ordering, source image and shipped resolve SPIR-V.
  void ClearRows(uint32_t top, uint32_t end) {
    if (top >= end) return;
    VkAttachmentDescription attachment{};
    attachment.format = VK_FORMAT_R8G8B8A8_UNORM;
    attachment.samples = VkSampleCountFlagBits(samples);
    attachment.loadOp = VK_ATTACHMENT_LOAD_OP_LOAD;
    attachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    attachment.initialLayout = attachment.finalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    VkAttachmentReference reference{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription subpass{};subpass.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount=1;subpass.pColorAttachments=&reference;
    VkSubpassDependency dependencies[2]{};
    dependencies[0]={VK_SUBPASS_EXTERNAL,0,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,VK_ACCESS_SHADER_READ_BIT,VK_ACCESS_COLOR_ATTACHMENT_READ_BIT|VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,0};
    dependencies[1]={0,VK_SUBPASS_EXTERNAL,VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,VK_ACCESS_SHADER_READ_BIT,0};
    VkRenderPassCreateInfo info{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
    info.attachmentCount=1;info.pAttachments=&attachment;info.subpassCount=1;info.pSubpasses=&subpass;info.dependencyCount=2;info.pDependencies=dependencies;
    VkRenderPass pass{};OK(vkCreateRenderPass(gpu.device,&info,nullptr,&pass));
    VkFramebufferCreateInfo fb{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};fb.renderPass=pass;fb.attachmentCount=1;fb.pAttachments=&view;fb.width=width;fb.height=height;fb.layers=1;
    VkFramebuffer framebuffer{};OK(vkCreateFramebuffer(gpu.device,&fb,nullptr,&framebuffer));
    gpu.Begin();VkRenderPassBeginInfo begin{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};begin.renderPass=pass;begin.framebuffer=framebuffer;begin.renderArea.extent={width,height};
    vkCmdBeginRenderPass(gpu.command,&begin,VK_SUBPASS_CONTENTS_INLINE);
    VkClearAttachment clear{};clear.aspectMask=VK_IMAGE_ASPECT_COLOR_BIT;
    VkClearRect rect{{{0,int32_t(top)},{width,end-top}},0,1};
    vkCmdClearAttachments(gpu.command,1,&clear,1,&rect);vkCmdEndRenderPass(gpu.command);gpu.Submit();
    vkDestroyFramebuffer(gpu.device,framebuffer,nullptr);vkDestroyRenderPass(gpu.device,pass,nullptr);
  }
  ~Source() {
    vkDestroyImageView(gpu.device, view, nullptr);
    if (stencil) vkDestroyImageView(gpu.device, stencil, nullptr);
    vkDestroyImage(gpu.device, image, nullptr);
    vkFreeMemory(gpu.device, memory, nullptr);
  }
  static uint32_t Pattern(uint32_t x, uint32_t y) {
    return (x + 1) | ((y + 1) << 8) | (((x + y) % 255) << 16) | 0xff000000u;
  }
  uint32_t Expected(uint32_t x, uint32_t y) {
    return depth ? 0x800000a5u : uniform ? 0xffbf8040u : Pattern(x, y);
  }
};
}  // namespace
int main() {
  try {
    Gpu gpu;
    size_t checks = 0;
    for (bool depth : {false, true}) {
      VkPipeline generic =
          depth ? gpu.Pipeline(fh1_native_resolve_memory_depth_cs,
                               sizeof(fh1_native_resolve_memory_depth_cs))
                : gpu.Pipeline(fh1_native_resolve_memory_color_cs,
                               sizeof(fh1_native_resolve_memory_color_cs));
      VkPipeline transfer =
          depth ? gpu.Pipeline(fh1_native_transfer_words_depth_cs,
                               sizeof(fh1_native_transfer_words_depth_cs))
                : gpu.Pipeline(fh1_native_transfer_words_color_cs,
                               sizeof(fh1_native_transfer_words_color_cs));
      for (uint32_t percent = 25; percent <= 100; percent++) {
        constexpr uint32_t w = 80, h = 32;
        uint32_t sw = (w * percent + 99) / 100, sh = (h * percent + 99) / 100;
        Source source(gpu, sw, sh, depth);
        uint32_t layout = (1u << 11) | (depth ? 1u << 22 : 0),
                 owner = layout | (1u << 30),
                 info = (2u << 16) | ((100 - percent) << 23) | (depth ? 4 : 0);
        uint32_t push[8] = {0, w | (h << 16), layout, owner, 0, info, 0, w};
        std::array<uint32_t, 5> spec = {1, layout, owner, 0,
                                        info | 0x80000000u};
        VkPipeline specialized =
            depth ? gpu.Pipeline(fh1_native_resolve_memory_depth_cs,
                                 sizeof(fh1_native_resolve_memory_depth_cs),
                                 &spec)
                  : gpu.Pipeline(fh1_native_resolve_memory_color_cs,
                                 sizeof(fh1_native_resolve_memory_color_cs),
                                 &spec);
        for (VkPipeline pipeline : {generic, specialized}) {
          gpu.Dispatch(pipeline, push, w, h, 16);
          auto* out = static_cast<uint32_t*>(gpu.mapped);
          for (uint32_t y = 0; y < h; y++)
            for (uint32_t x = 0; x < w; x++) {
              if (out[Tiled(x, y, w) / 4] !=
                  source.Expected(x * percent / 100, y * percent / 100))
                throw std::runtime_error(
                    "resolve mismatch " + std::to_string(percent) + "% " +
                    (depth ? "depth" : "colour") + " at " + std::to_string(x) +
                    "," + std::to_string(y));
              checks++;
            }
        }
        uint32_t transfer_push[8] = {
            0, sw | (sh << 16), layout, owner, (100 - percent) << 14, sw, 1, 0};
        gpu.Dispatch(transfer, transfer_push, sw, sh, 8);
        auto* out = static_cast<uint32_t*>(gpu.mapped);
        for (uint32_t y = 0; y < sh; y++)
          for (uint32_t x = 0; x < sw; x++) {
            if (out[y * sw + x] != source.Expected(x, y))
              throw std::runtime_error("transfer mismatch " +
                                       std::to_string(percent) + "% " +
                                       (depth ? "depth" : "colour"));
            checks++;
          }
        vkDestroyPipeline(gpu.device, specialized, nullptr);
      }
      vkDestroyPipeline(gpu.device, generic, nullptr);
      vkDestroyPipeline(gpu.device, transfer, nullptr);
    }
    size_t msaa_checks=0;
    VkPipeline ms_resolve=gpu.Pipeline(fh1_native_resolve_memory_color_ms_cs,sizeof(fh1_native_resolve_memory_color_ms_cs));
    VkPipeline ss_resolve=gpu.Pipeline(fh1_native_resolve_memory_color_cs,sizeof(fh1_native_resolve_memory_color_cs));
    for(int percent=25;percent<=100;percent++)for(uint32_t guest_msaa:{1u,2u})for(uint32_t samples:{1u,4u}){
      rex::graphics::Fh1RasterScale raster(percent);
      Source source(gpu,raster.Pixels(40),raster.Pixels(32),false,samples,true);
      uint32_t layout=(1u<<11)|(guest_msaa<<19),mode=samples==1?3u:guest_msaa==1?2u:0u;
      uint32_t owner=layout|(mode<<27)|(1u<<30);
      for(uint32_t select:{0u,4u,6u}){
        if(guest_msaa==1&&select==6)continue;
        uint32_t push[8]={0,40u|(32u<<16),layout,owner,select,(2u<<16)|((100-percent)<<23),0,40};
        gpu.Dispatch(samples==1?ss_resolve:ms_resolve,push,40,32,16);
        const auto* out=static_cast<const uint32_t*>(gpu.mapped);
        for(uint32_t y=0;y<32;y++)for(uint32_t x=0;x<40;x++){
          if(out[Tiled(x,y,40)/4]!=source.Expected(x,y))throw std::runtime_error("MSAA resolve mismatch "+std::to_string(percent)+"% guest "+std::to_string(guest_msaa)+" host "+std::to_string(samples));
          msaa_checks++;
        }
      }
    }
    vkDestroyPipeline(gpu.device,ms_resolve,nullptr);vkDestroyPipeline(gpu.device,ss_resolve,nullptr);
    std::cout<<"PASS: "<<msaa_checks<<" colour MSAA pixels; guest 2x/4x, host single/4x and sample averaging at 25-100%"<<std::endl;
    size_t legacy_seams=0, band_checks=0;
    VkPipeline resolve=gpu.Pipeline(fh1_native_resolve_memory_color_cs,sizeof(fh1_native_resolve_memory_color_cs));
    for (int percent=25;percent<=100;percent++) {
      rex::graphics::Fh1RasterScale raster(percent);
      for (bool legacy : {true,false}) {
        Source source(gpu,raster.Pixels(80),raster.Pixels(720),false);
        for (uint32_t band=0;band<3;band++) {
          const uint32_t layout=1u<<11,owner=layout|(1u<<30);
          uint32_t push[8]={0,80u|(240u<<16),layout,owner,(band*240)<<16,(2u<<16)|((100-percent)<<23),0,80};
          gpu.Dispatch(resolve,push,80,240,16);
          const auto* out=static_cast<const uint32_t*>(gpu.mapped);
          for (uint32_t y=0;y<240;y++)for(uint32_t x=0;x<80;x++) {
            bool same=out[Tiled(x,y,80)/4]==Source::Pattern(x*percent/100,(y+band*240)*percent/100);
            if(legacy){if(!same)legacy_seams++;}
            else {if(!same)throw std::runtime_error("band resolve seam at "+std::to_string(percent)+"% band "+std::to_string(band));band_checks++;}
          }
          const int end=(band+1)*240;
          source.ClearRows(raster.Origin(band*240),legacy?raster.End(end):raster.ClearEnd(end,band<2));
        }
      }
    }
    vkDestroyPipeline(gpu.device,resolve,nullptr);
    if(!legacy_seams)throw std::runtime_error("seam fixture did not reproduce the old clear corruption");
    std::cout<<"PASS: "<<band_checks<<" tiled-resolve pixels; reproduced "<<legacy_seams<<" corrupt pixels with old clears, zero with corrected clears (25-100%)"<<std::endl;
    std::cout << "PASS: " << checks
              << " colour/depth pixels; 25–100%, generic/specialized resolves "
                 "and transfers"
              << std::endl;
    return 0;
  } catch (const std::exception& e) {
    std::cerr << e.what() << std::endl;
    return 1;
  }
}
