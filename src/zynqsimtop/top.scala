package zynqsimSimTop

import chisel3._
import zynq._
import chisel3.util._
import chisel3.iotesters._
import chisel3.testers._
import config._
import Common._
import diplomacy._
import Common.Util._
import ReferenceChipBackend._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import junctions._
import uncore.axi4._
import unittest._
import junctions.NastiConstants._
import uncore.tilelink2._
import config.{Parameters, Field}
import RV32_3stage._
import RV32_3stage.Constants._

/** This includes the clock and reset as these are passed through the
  *  hierarchy until the Debug Module is actually instantiated. 
  *  
  */

class SimDTMonAXI4(implicit p: Parameters) extends BlackBox {
  val io = IO(new Bundle {
      val clk = Input(Clock())
      val reset = Input(Bool())
      val debug = AXI4BlindInputNode(Seq(AXI4MasterPortParameters(
    masters = Seq(AXI4MasterParameters(name = "AXI4 periphery"))))).bundleIn(0)
      val exit = Output(UInt(32.W))
    })

  def connect(tbclk: Clock, tbreset: Bool, dutio: io.debug.cloneType, tbsuccess: Bool) = {
    io.clk := tbclk
    io.reset := tbreset
    dutio <> io.debug 

    tbsuccess := io.exit === 1.U
    when (io.exit >= 2.U) {
      printf("*** FAILED *** (exit code = %d)\n", io.exit >> 1.U)
      //stop(1)
    }
  }
}

class SimTop extends Module {
  implicit val inParams = new WithZynqAdapter
  val tileouter = LazyModule(new SodorTile()(inParams))
  val tile = tileouter.module
  val io = IO(new Bundle {
    val success = Output(Bool())
  })
  val dtm = Module(new SimDTMonAXI4()(inParams)).connect(clock, reset.toBool, tile.io.ps_slave, io.success)
}

object elaborate extends ChiselFlatSpec{
  def main(args: Array[String]): Unit = {
    implicit val inParams = new WithZynqAdapter
    chisel3.Driver.execute(args, () => new SimTop)
  }
}