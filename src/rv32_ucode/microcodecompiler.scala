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


package Sodor
{

import Chisel._
import Node._

import Common.Instructions._
import scala.collection.mutable.ArrayBuffer


abstract class MicroOp();
case class Label(name: String) extends MicroOp;
case class Signals(ctrl_bits: Bits, label: String = "X") extends MicroOp;
    
object MicrocodeCompiler
{

   // run through instructions.scala, build mapping bewteen 
   // Instruction Name (String) -> Bits
   // used for building opcodeDispatchTable
   def generateInstructionList (): Map[String, UInt] =
   {
      var inst_list = Map[String, UInt]();
      val instClass = Common.Instructions.getClass();
      val b = UInt(0,32);
      val bitsClass = b.getClass();
      
      for (m <- instClass.getMethods()) 
      {
         val name = m.getName();
         val rtype = m.getReturnType();
         if (rtype == bitsClass) 
         {
            val i = m.invoke(Common.Instructions);
            inst_list += ((name, i.asInstanceOf[UInt])); 
         }
      }
      
      return inst_list
   }

   def generateDispatchTable (labelTargets: Map[String,Int]): Array[(UInt, UInt)]=
   {
      println("Generating Opcode Dispatch Table...");
      var dispatch_targets = ArrayBuffer[(UInt, UInt)]();
      val inst_list        = generateInstructionList();
                                            
      for ((inst_str, inst_bits) <- inst_list)
      {
         if (labelTargets.contains(inst_str))
         {
            printf("  Inst: %5s Addr: %d\n", inst_str,  labelTargets(inst_str));
            dispatch_targets += ((inst_bits -> UInt(labelTargets(inst_str))));
         }
      }

      //for debugging purposes, print out unused labels to verify we didn't
      //fail at matching an instructure
      var unused_targets   = ArrayBuffer[String]();
      for ((label_str, addr) <- labelTargets)
      {
         if (!inst_list.contains(label_str))
            unused_targets += label_str;
      }
      
      println("");
      println("Unused Labels for Dispatching:"); 
      println("    (Verify no instruction labels made it here by accident)");
      println("");
      println("   " +  unused_targets);
      println("");
      
      return dispatch_targets.toArray
   }

   // returns a tuple
   //    Mapping from "LabelString" to micro-address
   //    and LabelSz (log of largest micro-address)
   def constructLabelTargetMap(uop_insts: Array[MicroOp]): (Map[String,Int], Int)  = 
   {
      println("Building Microcode labelTargetMap...");
      
      var label_map = Map[String,Int]();
      var uaddr = 0;
      for (uop_inst <- uop_insts) 
      {  
         uop_inst match 
         {
            case Label(name)       => label_map += ((name, uaddr)); 
                                      printf("  Label: %7s, @%d\n", name, uaddr);
            case Signals(code,str) => uaddr += 1; 
         }
      }
      println("  MicroROM size    : " + (uaddr-1) + " lines");
      println("  Bitwidth of uaddr: " + log2Up(uaddr-1) + " bits");
      println("");
      return (label_map, log2Up(uaddr-1));
   }

   
   def emitRomBits(uop_lines: Array[MicroOp], labelTargets: Map[String,Int], label_sz: Int): Array[Bits] =
   {
      printf("Building Microcode ROM...\n");
      
      var buf = ArrayBuffer[Bits]();

      for (uop_line <- uop_lines) 
      {  
         uop_line match 
         {
            case Label(name) => 
            case Signals(ctrl_bits, "X") => 
               val line = Cat(ctrl_bits, Bits(0, label_sz));
               buf += (line);


            case Signals(ctrl_bits, label) => 
               val line = Cat(ctrl_bits, Bits(labelTargets(label),label_sz));
               buf += (line);
         }
      }
   
      println("");
      return buf.toArray;
   }

}

}


