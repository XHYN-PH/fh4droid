// Execute the renderer's shipped fallback SPIR-V on synthetic BC blocks.
// No game textures, reference GPU timings or phone qualification implied.
#include <vulkan/vulkan.h>
#include <algorithm>
#include <array>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <vector>
#include "src/graphics/shaders/vulkan_spirv/texture_load_dxt1_rgba8_cs.h"
#include "src/graphics/shaders/vulkan_spirv/texture_load_dxt5_rgba8_cs.h"
#include "src/graphics/shaders/vulkan_spirv/texture_load_dxn_rg8_cs.h"
#include "src/graphics/shaders/vulkan_spirv/texture_load_32bpb_cs.h"

#define CHECK_VK(call) do{auto result=(call);if(result!=VK_SUCCESS)throw std::runtime_error(#call+std::string(": ")+std::to_string(result));}while(0)
namespace {
struct Gpu {
  static constexpr size_t capacity=1<<20;
  VkInstance instance{};VkPhysicalDevice physical{};VkDevice device{};VkQueue queue{};
  uint32_t family{};VkCommandPool pool{};VkCommandBuffer command{};
  VkDescriptorSetLayout descriptor_layout{};VkPipelineLayout layout{};VkDescriptorPool descriptors{};
  VkDescriptorSet sets[2]{};VkBuffer buffers[2]{};VkDeviceMemory memory[2]{};void* mapped[2]{};
  Gpu(){
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};app.apiVersion=VK_API_VERSION_1_1;
    VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};ci.pApplicationInfo=&app;CHECK_VK(vkCreateInstance(&ci,nullptr,&instance));
    uint32_t count=0;CHECK_VK(vkEnumeratePhysicalDevices(instance,&count,nullptr));if(!count)throw std::runtime_error("No Vulkan GPU");std::vector<VkPhysicalDevice> devices(count);CHECK_VK(vkEnumeratePhysicalDevices(instance,&count,devices.data()));physical=devices.front();
    VkPhysicalDeviceProperties properties{};vkGetPhysicalDeviceProperties(physical,&properties);std::cout<<"Texture decode GPU: "<<properties.deviceName<<'\n';
    vkGetPhysicalDeviceQueueFamilyProperties(physical,&count,nullptr);std::vector<VkQueueFamilyProperties> queues(count);vkGetPhysicalDeviceQueueFamilyProperties(physical,&count,queues.data());
    while(family<count&&!(queues[family].queueFlags&VK_QUEUE_COMPUTE_BIT))++family;if(family==count)throw std::runtime_error("No compute queue");
    float priority=1;VkDeviceQueueCreateInfo qi{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};qi.queueFamilyIndex=family;qi.queueCount=1;qi.pQueuePriorities=&priority;
    VkDeviceCreateInfo di{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};di.queueCreateInfoCount=1;di.pQueueCreateInfos=&qi;CHECK_VK(vkCreateDevice(physical,&di,nullptr,&device));vkGetDeviceQueue(device,family,0,&queue);
    VkCommandPoolCreateInfo pc{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};pc.queueFamilyIndex=family;pc.flags=VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;CHECK_VK(vkCreateCommandPool(device,&pc,nullptr,&pool));
    VkCommandBufferAllocateInfo ca{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};ca.commandPool=pool;ca.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY;ca.commandBufferCount=1;CHECK_VK(vkAllocateCommandBuffers(device,&ca,&command));
    VkDescriptorSetLayoutBinding binding{0,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,1,VK_SHADER_STAGE_COMPUTE_BIT,nullptr};VkDescriptorSetLayoutCreateInfo lc{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};lc.bindingCount=1;lc.pBindings=&binding;CHECK_VK(vkCreateDescriptorSetLayout(device,&lc,nullptr,&descriptor_layout));
    VkDescriptorSetLayout layouts[]={descriptor_layout,descriptor_layout};VkPushConstantRange range{VK_SHADER_STAGE_COMPUTE_BIT,0,40};VkPipelineLayoutCreateInfo pl{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};pl.setLayoutCount=2;pl.pSetLayouts=layouts;pl.pushConstantRangeCount=1;pl.pPushConstantRanges=&range;CHECK_VK(vkCreatePipelineLayout(device,&pl,nullptr,&layout));
    VkDescriptorPoolSize size{VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,2};VkDescriptorPoolCreateInfo dp{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};dp.maxSets=2;dp.poolSizeCount=1;dp.pPoolSizes=&size;CHECK_VK(vkCreateDescriptorPool(device,&dp,nullptr,&descriptors));
    VkDescriptorSetAllocateInfo da{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};da.descriptorPool=descriptors;da.descriptorSetCount=2;da.pSetLayouts=layouts;CHECK_VK(vkAllocateDescriptorSets(device,&da,sets));
    VkPhysicalDeviceMemoryProperties memories{};vkGetPhysicalDeviceMemoryProperties(physical,&memories);
    for(unsigned i=0;i<2;i++){
      VkBufferCreateInfo bc{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};bc.size=capacity;bc.usage=VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;CHECK_VK(vkCreateBuffer(device,&bc,nullptr,&buffers[i]));VkMemoryRequirements requirements{};vkGetBufferMemoryRequirements(device,buffers[i],&requirements);
      uint32_t type=0;for(;type<memories.memoryTypeCount;type++)if((requirements.memoryTypeBits&(1u<<type))&&(memories.memoryTypes[type].propertyFlags&(VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT))==(VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT))break;
      if(type==memories.memoryTypeCount)throw std::runtime_error("No coherent memory");VkMemoryAllocateInfo ma{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};ma.allocationSize=requirements.size;ma.memoryTypeIndex=type;CHECK_VK(vkAllocateMemory(device,&ma,nullptr,&memory[i]));CHECK_VK(vkBindBufferMemory(device,buffers[i],memory[i],0));CHECK_VK(vkMapMemory(device,memory[i],0,VK_WHOLE_SIZE,0,&mapped[i]));
      VkDescriptorBufferInfo bi{buffers[i],0,capacity};VkWriteDescriptorSet write{VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};write.dstSet=sets[i];write.dstBinding=0;write.descriptorCount=1;write.descriptorType=VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;write.pBufferInfo=&bi;vkUpdateDescriptorSets(device,1,&write,0,nullptr);
    }
  }
  ~Gpu(){if(!device)return;vkDeviceWaitIdle(device);for(unsigned i=0;i<2;i++){if(mapped[i])vkUnmapMemory(device,memory[i]);vkDestroyBuffer(device,buffers[i],nullptr);vkFreeMemory(device,memory[i],nullptr);}vkDestroyDescriptorPool(device,descriptors,nullptr);vkDestroyPipelineLayout(device,layout,nullptr);vkDestroyDescriptorSetLayout(device,descriptor_layout,nullptr);vkDestroyCommandPool(device,pool,nullptr);vkDestroyDevice(device,nullptr);vkDestroyInstance(instance,nullptr);}
  VkPipeline pipeline(const uint32_t* code,size_t bytes){
    VkShaderModuleCreateInfo mc{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};mc.codeSize=bytes;mc.pCode=code;VkShaderModule module{};CHECK_VK(vkCreateShaderModule(device,&mc,nullptr,&module));VkComputePipelineCreateInfo pc{VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};pc.stage={VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};pc.stage.stage=VK_SHADER_STAGE_COMPUTE_BIT;pc.stage.module=module;pc.stage.pName="main";pc.layout=layout;VkPipeline result{};VkResult status=vkCreateComputePipelines(device,VK_NULL_HANDLE,1,&pc,nullptr,&result);vkDestroyShaderModule(device,module,nullptr);CHECK_VK(status);return result;
  }
  size_t test(VkPipeline pipeline,std::vector<uint8_t> block,std::vector<uint8_t> expected,bool compressed,uint32_t endian,bool tiled){
    // Endian conversions are involutions; encode each 32-bit word for the
    // guest order, then expect the decoder to recover the original block.
    for(size_t i=0;i<block.size();i+=4){if(endian==1){std::swap(block[i],block[i+1]);std::swap(block[i+2],block[i+3]);}else if(endian==2){std::swap(block[i],block[i+3]);std::swap(block[i+1],block[i+2]);}else if(endian==3){std::swap(block[i],block[i+2]);std::swap(block[i+1],block[i+3]);}}
    for(size_t i=0;i<capacity;i++)static_cast<uint8_t*>(mapped[1])[i]=block[i%block.size()];std::memset(mapped[0],0xcd,capacity);
    constexpr uint32_t width=128,height=128,offset=256;uint32_t blocks=compressed?32:128;
    uint32_t constants[]={uint32_t(tiled)|(endian<<2)|(1<<4)|(1<<7),offset,tiled?blocks:blocks*uint32_t(block.size()),32,blocks,blocks,1,offset,width*uint32_t(expected.size()),height};
    CHECK_VK(vkResetCommandBuffer(command,0));VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};CHECK_VK(vkBeginCommandBuffer(command,&begin));vkCmdBindPipeline(command,VK_PIPELINE_BIND_POINT_COMPUTE,pipeline);vkCmdBindDescriptorSets(command,VK_PIPELINE_BIND_POINT_COMPUTE,layout,0,2,sets,0,nullptr);vkCmdPushConstants(command,layout,VK_SHADER_STAGE_COMPUTE_BIT,0,sizeof(constants),constants);vkCmdDispatch(command,32,4,1);
    VkMemoryBarrier barrier{VK_STRUCTURE_TYPE_MEMORY_BARRIER};barrier.srcAccessMask=VK_ACCESS_SHADER_WRITE_BIT;barrier.dstAccessMask=VK_ACCESS_HOST_READ_BIT;vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_PIPELINE_STAGE_HOST_BIT,0,1,&barrier,0,nullptr,0,nullptr);CHECK_VK(vkEndCommandBuffer(command));VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};submit.commandBufferCount=1;submit.pCommandBuffers=&command;VkFenceCreateInfo fc{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};VkFence fence{};CHECK_VK(vkCreateFence(device,&fc,nullptr,&fence));CHECK_VK(vkQueueSubmit(queue,1,&submit,fence));CHECK_VK(vkWaitForFences(device,1,&fence,VK_TRUE,10000000000ULL));vkDestroyFence(device,fence,nullptr);
    size_t length=width*height*expected.size();auto* output=static_cast<const uint8_t*>(mapped[0]);for(size_t i=0;i<capacity;i++){uint8_t wanted=i<offset||i>=offset+length?0xcd:expected[(i-offset)%expected.size()];if(output[i]!=wanted)throw std::runtime_error("decode mismatch byte "+std::to_string(i)+" expected "+std::to_string(wanted)+" got "+std::to_string(output[i])+" endian "+std::to_string(endian)+" tiled "+std::to_string(tiled));}return capacity;
  }
};
}
int main(){try{
  Gpu gpu;size_t checks=0;
  struct Case{const char* name;const uint32_t* code;size_t bytes;std::vector<uint8_t> block,expected;bool compressed;};
  std::vector<Case> cases={
    {"BC1 red",texture_load_dxt1_rgba8_cs,sizeof(texture_load_dxt1_rgba8_cs),{0,248,224,7,0,0,0,0},{255,0,0,255},true},
    {"BC1 interpolated",texture_load_dxt1_rgba8_cs,sizeof(texture_load_dxt1_rgba8_cs),{0,248,224,7,170,170,170,170},{170,85,0,255},true},
    {"BC1 transparent",texture_load_dxt1_rgba8_cs,sizeof(texture_load_dxt1_rgba8_cs),{0,0,0,248,255,255,255,255},{0,0,0,0},true},
    {"BC3 alpha",texture_load_dxt5_rgba8_cs,sizeof(texture_load_dxt5_rgba8_cs),{255,0,0,0,0,0,0,0,0,248,224,7,0,0,0,0},{255,0,0,255},true},
    {"BC5 channels",texture_load_dxn_rg8_cs,sizeof(texture_load_dxn_rg8_cs),{128,32,0,0,0,0,0,0,255,0,0,0,0,0,0,0},{128,255},true},
    {"RGBA8",texture_load_32bpb_cs,sizeof(texture_load_32bpb_cs),{19,67,131,239},{19,67,131,239},false},
  };
  for(const auto& test:cases){VkPipeline pipeline=gpu.pipeline(test.code,test.bytes);for(uint32_t endian=0;endian<4;endian++)for(bool tiled:{false,true})checks+=gpu.test(pipeline,test.block,test.expected,test.compressed,endian,tiled);vkDestroyPipeline(gpu.device,pipeline,nullptr);std::cout<<"PASS: "<<test.name<<" linear/tiled, four byte orders\n";}
  std::cout<<"PASS: "<<checks<<" texture decode and output guard byte comparisons\n";return 0;
}catch(const std::exception& error){std::cerr<<error.what()<<'\n';return 1;}}
