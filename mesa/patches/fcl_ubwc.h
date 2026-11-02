/*
 * FCL: 细粒度 UBWC/CCU 调试开关（默认全部关闭 = 保守行为）。
 * 只能控制我们为 KGSL 补的几处保守逻辑，改完需要重启游戏。
 */
#ifndef FCL_UBWC_H
#define FCL_UBWC_H

#include <stdbool.h>
#include <stdlib.h>

enum {
   FCL_UBWC_NO_BARRIER = 0,      /* 不对 CCU clean/invalidate 补 UCHE invalidate/WFI */
   FCL_UBWC_NO_READ_CLEAR,       /* 采样前不检查/清除 UBWC flag */
   FCL_UBWC_NO_SAMEBATCH_FLUSH,  /* 同 batch 写后读不 flush 整批 */
   FCL_UBWC_NO_UNCOMPRESS,       /* 格式重解释时不做 uncompress/shadow（危险） */
   FCL_UBWC_DROP_DEFERRED_CLEAR, /* batch 已提交时直接丢弃待清 flag */
   FCL_UBWC_TRUST_BO_FLAGS,      /* 信任 BO cache 里旧 flag，跳过 needs_ubwc_clear */
   FCL_UBWC_OPT_COUNT,
};

static inline bool
fcl_ubwc_opt(unsigned id)
{
   static const char *names[FCL_UBWC_OPT_COUNT] = {
      "FCL_UBWC_NO_BARRIER",
      "FCL_UBWC_NO_READ_CLEAR",
      "FCL_UBWC_NO_SAMEBATCH_FLUSH",
      "FCL_UBWC_NO_UNCOMPRESS",
      "FCL_UBWC_DROP_DEFERRED_CLEAR",
      "FCL_UBWC_TRUST_BO_FLAGS",
   };
   static signed char cache[FCL_UBWC_OPT_COUNT];
   static bool init;

   if (!init) {
      for (unsigned i = 0; i < FCL_UBWC_OPT_COUNT; i++)
         cache[i] = getenv(names[i]) != NULL;
      init = true;
   }
   return cache[id] != 0;
}

#endif
