#pragma once
#include <functional>
#include <string>
namespace pinyon_shift::android {
void InstallBridge(std::function<void(bool)> menu,std::function<void()> settings,
                   std::function<std::string()> achievements,
                   std::function<void(const std::string&)> action);
void RefreshAchievements();
void ClearBridge();
void RecordCleanExit();
}
