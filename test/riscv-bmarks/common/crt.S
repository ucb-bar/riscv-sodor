   .data
   .globl	_heapend
   .globl	environ
_heapend:
   .word	0
environ:
   .word	0

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
   

   mfpcr x1,cr0
   andi x1,x1, 0xF7F
   mtpcr x1,cr0

   la sp,stacktop
   la	sp,0x1FFF0
   jal	main
   nop
   nop
   
     
   .bss
   .globl stacktop

   .align 4
#currently this is disasbled, because I'm impatient 
#and don't want to load in this empty space
#  .skip 131072
stacktop:
   
          
