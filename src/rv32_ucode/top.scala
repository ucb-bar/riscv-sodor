package Sodor

import chisel3._
import chisel3.util._
import config._
import Constants._
import Common._
import Common.Util._
import ReferenceChipBackend._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap


object ReferenceChipBackend {
  val initMap = new HashMap[Module, Bool]()
}


class Top extends Module 
{
   val io = IO(new Bundle{
      val success = Output(Bool())
   })

   implicit val sodor_conf = new SodorConfiguration

   val tile = Module(new SodorTile()(sodor_conf))
   val dtm = Module(new SimDTM()(sodor_conf)).connect(clock, reset.toBool, tile.io.dmi, io.success)

}

object elaborate {
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(args, () => new Top)
  }
}
