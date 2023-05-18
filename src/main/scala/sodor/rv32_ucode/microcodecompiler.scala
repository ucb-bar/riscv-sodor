//**************************************************************************
// RISCV U-Code Compiler
//--------------------------------------------------------------------------
//
// Jonathan Bachrach
// Christopher Celio
// 2012 Feb 4
//
//
//
// This file contains the methods required for compiling the micro-code found in
// "microcode.scala" down to a ROM.  The micro-code programmer needs only specify
// the micro-code and labels at the beginning of the start of a macro-instruction
// sequence, and the micro-code compiler will generate the micro-code ROM bits,
// and patch in the label addresses (much like writing assembly, labels get
// turned into addresses).
//
// The compiler also generates the dispatch table, which requires analyzing the
// micro-code table and comparing which instruction labels were used that match
// up the instructions enumerated in "instructions.scala".
//


package sodor.ucode

import chisel3._
import chisel3.util._


import sodor.common.Instructions._
import scala.collection.mutable.ArrayBuffer


abstract class MicroOp()
case class Label(name: String) extends MicroOp
case class Signals(ctrl_bits: Bits, label: String = "X") extends MicroOp

object MicrocodeCompiler
{

   // run through instructions.scala, build mapping bewteen
   // Instruction Name (String) -> Bit Pattern
   // used for building opcodeDispatchTable
   def generateInstructionList (): Map[String, BitPat] =
   {
      var inst_list = Map[String, BitPat]()
      val instClass = sodor.common.Instructions.getClass()
      val b = BitPat("b?????????????????000?????1100011")
      val bitsClass = b.getClass()

      for (m <- instClass.getMethods())
      {
         val name = m.getName()
         val rtype = m.getReturnType()
         if (rtype == bitsClass)
         {
            val i = m.invoke(sodor.common.Instructions)
            inst_list += ((name, i.asInstanceOf[BitPat]))
         }
      }

      return inst_list
   }

   def generateDispatchTable (labelTargets: Map[String,Int]): Array[(BitPat, UInt)]=
   {
      println("Generating Opcode Dispatch Table...")
      var dispatch_targets = ArrayBuffer[(BitPat, UInt)]()
      val inst_list        = generateInstructionList()

      for ((inst_str, inst_bits) <- inst_list)
      {
         if (labelTargets.contains(inst_str))
         {
            dispatch_targets += ((inst_bits -> labelTargets(inst_str).U))
         }
      }

      //for debugging purposes, print out unused labels to verify we didn't
      //fail at matching an instructure
      var unused_targets   = ArrayBuffer[String]()
      for ((label_str, addr) <- labelTargets)
      {
         if (!inst_list.contains(label_str))
            unused_targets += label_str
      }

      println("")
      println("Unused Labels for Dispatching:")
      println("    (Verify no instruction labels made it here by accident)")
      println("")
      println("   " +  unused_targets)
      println("")

      return dispatch_targets.toArray
   }

   // returns a tuple
   //    Mapping from "LabelString" to micro-address
   //    and LabelSz (log of largest micro-address)
   def constructLabelTargetMap(uop_insts: Array[MicroOp]): (Map[String,Int], Int)  =
   {
      println("Building Microcode labelTargetMap...")

      var label_map = Map[String,Int]()
      var uaddr = 0
      for (uop_inst <- uop_insts)
      {
         uop_inst match
         {
            case Label(name)       => label_map += ((name, uaddr))
            case Signals(code,str) => uaddr += 1
         }
      }
      val label_sz = log2Ceil(uaddr)
      println("Label Map " + label_map)
      println("  MicroROM size    : " + uaddr + " lines")
      println("  Bitwidth of uaddr: " + label_sz + " bits")
      println("")
      return (label_map, label_sz)
   }


   def emitRomBits(uop_lines: Array[MicroOp], labelTargets: Map[String,Int], label_sz: Int): Array[Bits] =
   {
      //printf("Building Microcode ROM...\n")

      var buf = ArrayBuffer[Bits]()

      for (uop_line <- uop_lines)
      {
         uop_line match
         {
            case Label(name) =>
            case Signals(ctrl_bits, "X") =>
               val line = Cat(ctrl_bits, 0.U(label_sz.W))
               buf += (line)


            case Signals(ctrl_bits, label) =>
               val line = Cat(ctrl_bits, labelTargets(label).U(label_sz.W))
               buf += (line)
         }
      }

      println("")
      return buf.toArray
   }

}
