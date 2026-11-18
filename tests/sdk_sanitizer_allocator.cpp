// The official Linux host kit defines delete in terms of free. ASan's
// executable-side default new interceptor instead tags allocations as C++
// new, producing a mismatch at an otherwise matching kit free. Use the kit's
// malloc/free convention consistently for this mixed-library test harness.
// ASan bounds/use-after-free/leak detection and UBSan remain enabled. This is
// never linked into an Android package or used for performance measurements.
#include <cstdlib>
#include <new>

void* operator new(std::size_t size) {
  if (void* memory=std::malloc(size?size:1)) return memory;
  throw std::bad_alloc();
}
void* operator new[](std::size_t size) { return ::operator new(size); }
void operator delete(void* memory) noexcept { std::free(memory); }
void operator delete[](void* memory) noexcept { std::free(memory); }
void operator delete(void* memory,std::size_t) noexcept { std::free(memory); }
void operator delete[](void* memory,std::size_t) noexcept { std::free(memory); }
void* operator new(std::size_t size,std::align_val_t alignment) {
  void* memory=nullptr;
  if (posix_memalign(&memory,std::size_t(alignment),size?size:1)==0) return memory;
  throw std::bad_alloc();
}
void* operator new[](std::size_t size,std::align_val_t alignment) { return ::operator new(size,alignment); }
void operator delete(void* memory,std::align_val_t) noexcept { std::free(memory); }
void operator delete[](void* memory,std::align_val_t) noexcept { std::free(memory); }
void operator delete(void* memory,std::size_t,std::align_val_t) noexcept { std::free(memory); }
void operator delete[](void* memory,std::size_t,std::align_val_t) noexcept { std::free(memory); }
