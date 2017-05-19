//**************************************************************************
// Scratchpad Memory (asynchronous)
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2013 Jun 12
//
// Provides a variable number of ports to the core, and one port to the HTIF
// (host-target interface).
//
// Assumes that if the port is ready, it will be performed immediately.
// For now, don't detect write collisions.
//
// Optionally uses synchronous read (default is async). For example, a 1-stage
// processor can only ever work using asynchronous memory!

package Common
{

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import Constants._

trait MemoryOpConstants 
{
   val MT_X  = 0.asUInt(3.W)
   val MT_B  = 1.asUInt(3.W)
   val MT_H  = 2.asUInt(3.W)
   val MT_W  = 3.asUInt(3.W)
   val MT_D  = 4.asUInt(3.W)
   val MT_BU = 5.asUInt(3.W)
   val MT_HU = 6.asUInt(3.W)
   val MT_WU = 7.asUInt(3.W)

   val M_X   = "b0".asUInt(1.W)
   val M_XRD = "b0".asUInt(1.W) // int load
   val M_XWR = "b1".asUInt(1.W) // int store
}

// from the pov of the datapath
class MemPortIo(data_width: Int)(implicit conf: SodorConfiguration) extends Bundle 
{
   val req    = Decoupled(new MemReq(data_width))
   val resp   = Flipped(new ValidIO(new MemResp(data_width)))
  override def cloneType = { new MemPortIo(data_width).asInstanceOf[this.type] }
}

class MemReq(data_width: Int)(implicit conf: SodorConfiguration) extends Bundle
{
   val addr = Output(UInt(conf.xprlen.W))
   val data = Output(UInt(data_width.W))
   val fcn  = Output(UInt(M_X.getWidth.W))  // memory function code
   val typ  = Output(UInt(MT_X.getWidth.W)) // memory type
  override def cloneType = { new MemReq(data_width).asInstanceOf[this.type] }
}

class MemResp(data_width: Int) extends Bundle
{
   val data = Input(UInt(data_width.W))
  override def cloneType = { new MemResp(data_width).asInstanceOf[this.type] }
}

// NOTE: the default is enormous (and may crash your computer), but is bound by
// what the fesvr expects the smallest memory size to be.  A proper fix would
// be to modify the fesvr to expect smaller sizes.
class ScratchPadMemory(num_core_ports: Int, num_bytes: Int = (1 << 21), seq_read: Boolean = false)(implicit conf: SodorConfiguration) extends Module
{
   val io = IO(new Bundle
   {
      val core_ports = Vec(num_core_ports, Flipped(new MemPortIo(data_width = conf.xprlen)) )
      val htif_port = Flipped(new MemPortIo(data_width = 64))
   })


   // HTIF min packet size is 8 bytes 
   // but 32b core will access in 4 byte chunks
   // thus we will bank the scratchpad
   val num_bytes_per_line = 8
   val num_lines = num_bytes / num_bytes_per_line
   if (seq_read)
      println("\n    Sodor Tile: creating Synchronous Scratchpad Memory of size " + num_lines*num_bytes_per_line/1024 + " kB\n")
   else
      println("\n    Sodor Tile: creating Asynchronous Scratchpad Memory of size " + num_lines*num_bytes_per_line/1024 + " kB\n")
   var data_bank = if(seq_read)
      SyncReadMem(num_lines, Vec(num_bytes_per_line, UInt(8.W)))
   else 
      Mem(num_lines, Vec(num_bytes_per_line, UInt(8.W))) //for 1 stage and 2 stage need for combinational reads 


   // constants
   val idx_lsb = log2Ceil(num_bytes_per_line) 

   for (i <- 0 until num_core_ports)
   {
      if (seq_read) 
         io.core_ports(i).resp.valid := Reg(next = io.core_ports(i).req.valid)
      else 
         io.core_ports(i).resp.valid := io.core_ports(i).req.valid
      
      io.core_ports(i).req.ready := Bool(true) // for now, no back pressure 

      val req_valid      = io.core_ports(i).req.valid
      val req_addr       = io.core_ports(i).req.bits.addr
      val req_data       = io.core_ports(i).req.bits.data
      val req_fcn        = io.core_ports(i).req.bits.fcn
      val req_typ        = io.core_ports(i).req.bits.typ
      val byte_shift_amt = io.core_ports(i).req.bits.addr(2,0)
      val bit_shift_amt  = Cat(byte_shift_amt, UInt(0,3))

      // read access
      val data_idx = Wire(UInt())
      data_idx := req_addr >> idx_lsb.U
      val r_data_idx = Reg(next = data_idx)
      val read_data_out = Wire(Vec(num_bytes_per_line, UInt(8.W)))
      val rdata_out = Wire(UInt(32.W))

      if (seq_read)
      {
         read_data_out := data_bank.read(r_data_idx)
         rdata_out     := LoadDataGen(read_data_out, Reg(next=req_typ), Reg(next = req_addr(2,0)))
      }
      else
      {
         read_data_out := data_bank.read(data_idx)
         rdata_out     := LoadDataGen(read_data_out, req_typ, req_addr(2,0))
      }

      io.core_ports(i).resp.bits.data := rdata_out


      // write access
      when (req_valid && req_fcn === M_XWR)
      {
         // move the wdata into position on the sub-line
         val wdata = StoreDataGen(req_data, req_typ, req_addr(2,0)) 
         data_bank.write(data_idx, wdata, StoreMask(req_typ, req_addr(2,0)))
      }
      .elsewhen (req_valid && req_fcn === M_XRD){
         r_data_idx := data_idx
      }
   }  


   // HTIF -------
   
   io.htif_port.req.ready := Bool(true) // for now, no back pressure
   // synchronous read
   val htif_idx = Reg(UInt())
   htif_idx := io.htif_port.req.bits.addr >> UInt(idx_lsb)
   
   io.htif_port.resp.valid     := Reg(next=io.htif_port.req.valid && io.htif_port.req.bits.fcn === M_XRD)
   io.htif_port.resp.bits.data := data_bank(htif_idx)

   when (io.htif_port.req.valid && io.htif_port.req.bits.fcn === M_XWR)
   {
      data_bank(htif_idx) := io.htif_port.req.bits.data
   }

}



object StoreDataGen
{
   def apply(din: Bits, typ: UInt, idx: UInt): Vec[UInt] =
   {
      val word = (typ === MT_W) || (typ === MT_WU)
      val half = (typ === MT_H) || (typ === MT_HU)
      val byte_ = (typ === MT_B) || (typ === MT_BU)
      val dout = Wire(Vec(8, UInt(8.W)))
      dout := 0.U
      dout := Mux(!(word || half || byte_), din, 0.U)
      dout(idx) := din(7,0)
      dout(idx + 1.U) := Mux(half, din(15,8), 0.U)
      return dout
   }
}


object StoreMask
{
   def apply(sel: UInt, idx: UInt): Seq[Bool] = 
   {
      val word = (sel === MT_W) || (sel === MT_WU)
      val half = (sel === MT_H) || (sel === MT_HU)
      val byte = (sel === MT_B) || (sel === MT_BU)
      val wmask = Wire(UInt(8.W))
      wmask := Mux(!(word || byte || half), "b11111111".U, wmask)
      wmask(idx + 1.U) := Mux(half, 1.U, 0.U)
      wmask(idx) :=  1.U //for byte access
      wmask := Mux(word, 15.U << (idx(2) << 1.U) , wmask)
      return wmask.toBools
   }
}

//appropriately mask and sign-extend data for the core
object LoadDataGen
{
   def apply(data: Vec[UInt], typ: UInt, idx: UInt) : UInt =
   {
      val word = (typ === MT_W) || (typ === MT_WU)
      val half = (typ === MT_H) || (typ === MT_HU)
      val byte_ = (typ === MT_B) || (typ === MT_BU)
      val dout = Wire(UInt(32.W))
      dout(7,0) := data(idx)
      dout(15,8) := Mux(half, data(idx + 1.U), 0.U)
      dout(31,16) := Mux(word, Cat(data(idx + 3.U),data(idx + 2.U)), 0.U)
      return dout
   }
}

}
