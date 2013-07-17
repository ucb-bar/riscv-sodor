#include <stdint.h>
#include <stdio.h>
#include <DirectC.h>
#include <stdlib.h>
#include <string.h>
#include "disasm.h"

extern "C" void riscv_disasm(vc_handle inst, vc_handle dasm, vc_handle minidasm)
{
  static disassembler disasm;

  char str[1024];
  insn_t insn;
  vec32 tmp;
  vc_get4stVector(inst, (vec32*)&tmp);
  insn.bits = tmp.d;

  strcpy(str, disasm.disassemble(insn).c_str());

  for (int i = strlen(str); i < sizeof(str); i++)
    str[i] = ' ';
  str[vc_width(dasm)/8] = '\0';

  vc_StringToVector(str, dasm);

  char* space = strchr(str,' ');
  if (space)
    for (int i = space-str; i < sizeof(str); i++)
      str[i] = ' ';
  str[vc_width(minidasm)/8] = '\0';

  vc_StringToVector(str, minidasm);
}
