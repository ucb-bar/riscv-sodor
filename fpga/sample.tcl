# .text
#     lui     x1,0x40000    # 0x40000000
#     sw    x0,4(x1)    
#     li    x3,6
#     li     x4,10
#     li    x5,1
# star:
#     lw     x2,8(x1) # read button value
#     bne    x2,x5,temp 
#     sw     x3,0(x1) # write colour to rgb led
#     bne    x3,x4,star # repeat
# temp:
#     sw    x4,0(x1) # write colour to rgb led
#     bne    x3,x4,star # repeat

mwr 0x10000000 0x400000b7
mwr 0x10000004 0x0000a223
mwr 0x10000008 0x00600193
mwr 0x1000000C 0x00a00213
mwr 0x10000010 0x00100293
mwr 0x10000014 0x0080a103
mwr 0x10000018 0x00511663
mwr 0x1000001C 0x0030a023
mwr 0x10000020 0xfe419ae3
mwr 0x10000024 0x0040a023
mwr 0x10000028 0xfe4196e3
mwr -force 0x40000110 1
