#include "disasm-modelsim.h"
#include "disasm.h"

DPI_LINK_DECL DPI_DLLESPEC
void
riscv_disasm(
  const svBitVecVal* insn,
  svBitVecVal* dasm,
  svBitVecVal* minidasm)
{
  char str[1024];
  insn_t inst;

  inst.bits = insn;
  strcpy(str, disasm.disassemble(inst).c_str())

  for (int i = strlen(str); i < sizeof(str); i++)
    str[i] = ' ';

  for (int i = 32, j = 0; i; i--, j++)
    ((char*)dasm)[i-1] = str[j];

  char* space = strchr(str,' ');
  if (space)
    for (int i = space-str; i < sizeof(str); i++)
      str[i] = ' ';

  for (int i = 6, j = 0; i; i--, j++)
    ((char*)minidasm)[i-1] = str[j];
}
