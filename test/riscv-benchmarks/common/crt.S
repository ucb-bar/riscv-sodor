#include "encoding.h"

  .data
  .globl _heapend
  .globl environ
_heapend:
  .word 0
environ:
  .word 0

  .text
  .globl _start

_start:
  li  x1, 0
  li  x2, 0
  li  x3, 0
  li  x4, 0
  li  x5, 0
  li  x6, 0
  li  x7, 0
  li  x8, 0
  li  x9, 0
  li  x10,0
  li  x11,0
  li  x12,0
  li  x13,0
  li  x14,0
  li  x15,0
  li  x16,0
  li  x17,0
  li  x18,0
  li  x19,0
  li  x20,0
  li  x21,0
  li  x22,0
  li  x23,0
  li  x24,0
  li  x25,0
  li  x26,0
  li  x27,0
  li  x28,0
  li  x29,0
  li  x30,0
  li  x31,0

# Wait 8k cycles for BTB to reset, then set stats=1
  li  x1,1
  li  x2,12
  sll x1, x1, x2
init1:
  addi  x1, x1, -1
  bnez  x1, init1
  li    x1, 1
  csrw stats, 1
  li    x1, 0
  li    x2, 0

# Commented code sets up trap vector for processors supporting
# supervisor mode--add this back later when we support that again.

#1:
#
#  lui a0, %hi(trap_entry)    
#  add a0, a0, %lo(trap_entry)
#  csrw evec, a0
#
#  lui a0, %hi(main)    
#  add a0, a0, %lo(main)
#  csrw epc, a0
#


  # only allow core 0 to proceed
1:csrr a0, hartid
  bnez a0, 1b

  la  sp,stacktop
  
  # jmp to main as a user program
  jal main
  nop
  nop
1:b 1b

#.align 4
#.globl trap_entry
#trap_entry:                # only check for SYS_exit, otherwise crash out
#  li a3, 1337              # magic "bad things" happened error code
#  csrr a1, cause
#  li a2, 6                 # syscall exception number
#  bne a1, a2, exit_error
#handle_syscall:
#  li a1, 93                # SYS_exit number
#  bne v0, a1, exit_error
#  li a1, 1                 # successful exit code
#  move a3, a0
#  bne a3, a1, exit_error
#  csrw tohost, a1          # exit successfully (tohost == 1)
#1:b 1b
#exit_error:
#  sll a3, a3, 1
#  or  a3, a3, 1
#  csrw tohost, a3
#1:b 1b
#
  .bss
  .globl stacktop

  .align 4
  .skip 131072
stacktop:
