package Sodor

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import Constants._
import Common._
import Common.Util._
import ReferenceChipBackend._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap


object ReferenceChipBackend {
  val initMap = new HashMap[Module, Bool]()
}

class TopIO() extends Bundle  {
  val debug_stats_csr = Output(Bool())
  val htif  = new Common.HTIFIO()
}

class Top extends Module 
{
   val io = IO(new TopIO())

   implicit val sodor_conf = SodorConfiguration()
   val reset_signal = Reg(next=Reg(next=io.htif.reset))
   val tile = Module(new SodorTile)
   tile.io.host.reset := reset_signal
   tile.io.host.id := 0.U
   tile.io.host.csr_req <> Queue(io.htif.csr_req)
   io.htif.csr_rep <> tile.io.host.csr_rep
   io.htif.ipi_req <> tile.io.host.ipi_req
   io.htif.ipi_rep <> tile.io.host.ipi_rep
   tile.io.host.mem_req <> Queue(io.htif.mem_req)
   io.htif.mem_rep <> tile.io.host.mem_rep

   io.debug_stats_csr := Reg(next=tile.io.host.debug_stats_csr)
}

object elaborate extends ChiselFlatSpec{
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(args, () => new Top)
  }
}
