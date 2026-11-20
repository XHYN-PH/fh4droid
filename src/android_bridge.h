#pragma once
#include <cstdint>
#include <functional>
#include <string>
namespace pinyon_shift::android {
void InstallBridge(std::function<void(bool)> menu,std::function<void()> settings,
                   std::function<std::string()> achievements,
                   std::function<void(const std::string&)> action);
void RefreshAchievements();
void ClearBridge();
void RecordCleanExit();
void SetCoarseShadingAvailable(bool available);
void ReportCpuFrame(int64_t work_ns);
}
