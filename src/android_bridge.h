#pragma once
#include <functional>
namespace pinyon_shift::android {
void InstallBridge(std::function<void(bool)> menu,std::function<void()> settings);
void ClearBridge();
void RecordCleanExit();
}
