#*****************************************************************************
# Median filter (assembly version)
#-----------------------------------------------------------------------------
#
# Author : Christopher Batten
# Date   : February 8, 2006
#
# Simple 1D three element median filter benchmark ...
#

#--------------------------------------------------------------------------
# Headers and Defines
#--------------------------------------------------------------------------

#define rN             x2
#define rBasePtrInput  x3
#define rBasePtrOutput x4

#define rPtrInput      x5
#define rPtrOutput     x6
#define rCount         x7
#define rA             x8
#define rB             x9
#define rC             x10
#define rTemp          x11

#--------------------------------------------------------------------------
# median_asm( int n, int input[], int results[] )
#--------------------------------------------------------------------------

        .text
        .align 2
        .globl median_asm
        .type  median_asm,@function
#        .ent   median_asm

median_asm:

        # Number of output elements is n-2 (since we are working
        # with a 3 element median filter, there is no output value
        # for the first and last elemetns)

        mfpcr x1, cr0
        andi x1,x1, 0xF7F
        mtpcr x1, cr0

        addi  rCount, rN, 0
        addi rCount, rCount, -2

        # Zero the ends

        sw    x0, 0(rBasePtrOutput)

        sll  rTemp, rN, 2
        add  rTemp, rBasePtrOutput, rTemp
        sw    x0, -4(rTemp)

        # Initialize the input and output pointers

        addi  rPtrInput,  rBasePtrInput, 0
        addi  rPtrOutput, rBasePtrOutput, 0

        addi rPtrInput,  rPtrInput,  4
        addi rPtrOutput, rPtrOutput, 4



medianloop:

        # Load three elements from the input vector

        lw  rA, -4(rPtrInput)
        lw  rB,  0(rPtrInput)
        lw  rC,  4(rPtrInput)

        sltu rTemp, rA, rB      #. if ( A < B )
        beq  x0, rTemp, l1     #. {
                                #.
        sltu rTemp, rB, rC      #.   if ( B < C )
        beq  x0, rTemp, l2     #.   {
        sw   rB, 0(rPtrOutput)  #.     *rPtrOutput = B
        j    done               #.   }
                                #.
l2:     sltu rTemp, rC, rA      #.   else if ( C < A )
        beq  x0, rTemp, l3     #.   {
        sw   rA, 0(rPtrOutput)  #.     *rPtrOutput = A
        j    done               #.   }
                                #.
l3:     sw   rC, 0(rPtrOutput)  #.   else
        j    done               #.     *rPtrOutput = C
                                #.
                                #. }
                                #. else {
                                #.
l1:     sltu rTemp, rA, rC      #.   if ( A < C )
        beq  x0, rTemp, l4     #.   {
        sw   rA, 0(rPtrOutput)  #.     *rPtrOutput = A
        j    done               #.   }
                                #.
l4:     sltu rTemp, rC, rB      #.   else if ( C < B )
        beq  x0, rTemp, l5     #.   {
        sw   rB, 0(rPtrOutput)  #.     *rPtrOutput = B
        j    done               #.   }
                                #.
l5:     sw   rC, 0(rPtrOutput)  #.   else
                                #.     *rPtrOutput = C
                                #.
                                #.}

done:

        # Increment the pointers and repeat

        addi rPtrInput, rPtrInput,   4
        addi rPtrOutput, rPtrOutput, 4
        addi rCount, rCount, -1
        bne x0, rCount, medianloop


        # Return

        jalr x0,x1,0

        .end   median_asm
