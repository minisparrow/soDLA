package nvdla

import chisel3._
import chisel3.experimental._
import chisel3.util._

class NV_COUNTER_STAGE_histogram(width: Int) extends Module{

    val io = IO(new Bundle{
        val clk = Input(Clock())

        val rd_stall_inc = Input(Bool())
        val rd_stall_dec = Input(Bool())
        val rd_stall_clr = Input(Bool())
        val rd_stall_cen = Input(Bool())

        val cnt_cur = Output(UInt(width.W))

    })
//     
//          ┌─┐       ┌─┐
//       ┌──┘ ┴───────┘ ┴──┐
//       │                 │
//       │       ───       │
//       │  ─┬┘       └┬─  │
//       │                 │
//       │       ─┴─       │
//       │                 │
//       └───┐         ┌───┘
//           │         │
//           │         │
//           │         │
//           │         └──────────────┐
//           │                        │
//           │                        ├─┐
//           │                        ┌─┘    
//           │                        │
//           └─┐  ┐  ┌───────┬──┐  ┌──┘         
//             │ ─┤ ─┤       │ ─┤ ─┤         
//             └──┴──┘       └──┴──┘ 
withClock(io.clk){

    // adv logic
    val adv = io.rd_stall_inc ^ io.rd_stall_dec

    // cnt logic
    val cnt_cur_reg = RegInit("b0".asUInt(width.W))
    val cnt_ext = Wire(UInt((width+2).W))
    val cnt_inc = Wire(UInt((width+2).W))
    val cnt_dec = Wire(UInt((width+2).W))
    val cnt_mod = Wire(UInt((width+2).W))
    val cnt_new = Wire(UInt((width+2).W))
    val cnt_nxt = Wire(UInt((width+2).W))

    cnt_ext := cnt_cur_reg
    cnt_inc := cnt_cur_reg +& 1.U
    cnt_dec := cnt_cur_reg -& 1.U
    cnt_mod := Mux(io.rd_stall_inc && !io.rd_stall_dec, cnt_inc, 
                  Mux(!io.rd_stall_inc && io.rd_stall_dec, cnt_dec,
                  cnt_ext))
    cnt_new := Mux(adv, cnt_mod, cnt_ext)
    cnt_nxt := Mux(io.rd_stall_clr, 0.U, cnt_new)

    // stage flops
    when(io.rd_stall_cen){
        cnt_cur_reg := cnt_nxt
    }

    io.cnt_cur := cnt_cur_reg

}}


class NV_COUNTER_STAGE_lut(width: Int) extends Module{

    val io = IO(new Bundle{
        val clk = Input(Clock())

        val lut_en = Input(Bool())
        val op_en_load = Input(Bool())

        val hit_add = Input(Bool())
        val hit_sub = Input(Bool())

        val hit_cnt = Output(UInt(width.W))

    })
//     
//          ┌─┐       ┌─┐
//       ┌──┘ ┴───────┘ ┴──┐
//       │                 │
//       │       ───       │
//       │  ─┬┘       └┬─  │
//       │                 │
//       │       ─┴─       │
//       │                 │
//       └───┐         ┌───┘
//           │         │
//           │         │
//           │         │
//           │         └──────────────┐
//           │                        │
//           │                        ├─┐
//           │                        ┌─┘    
//           │                        │
//           └─┐  ┐  ┌───────┬──┐  ┌──┘         
//             │ ─┤ ─┤       │ ─┤ ─┤         
//             └──┴──┘       └──┴──┘ 
withClock(io.clk){

    // adv logic
    val hit_adv = io.hit_add =/= io.hit_sub

    // cnt logic
    val cnt_cur_reg = RegInit("b0".asUInt(width.W))
    val cnt_ext = Wire(UInt((width+2).W))
    val cnt_mod = Wire(UInt((width+2).W))
    val cnt_new = Wire(UInt((width+2).W))
    val cnt_nxt = Wire(UInt((width+2).W))

    cnt_ext := cnt_cur_reg
    cnt_mod := cnt_cur_reg +& io.hit_add -& io.hit_sub
    cnt_new := Mux(hit_adv, cnt_mod, cnt_ext)
    cnt_nxt := Mux(io.op_en_load, "b0".asUInt((width+2).W), cnt_new)

    // stage flops
    when(io.lut_en){
        cnt_cur_reg := cnt_nxt
    }

    io.hit_cnt := cnt_cur_reg

}}


class NV_COUNTER_STAGE_saturation extends Module{

    val io = IO(new Bundle{
        val clk = Input(Clock())

        val cvt_saturation_add = Input(UInt(5.W))
        val cvt_saturation_sub = Input(Bool())
        val cvt_saturation_clr = Input(Bool())
        val cvt_saturation_cen = Input(Bool())

        val cvt_saturation_cnt = Output(UInt(32.W))

    })
//     
//          ┌─┐       ┌─┐
//       ┌──┘ ┴───────┘ ┴──┐
//       │                 │
//       │       ───       │
//       │  ─┬┘       └┬─  │
//       │                 │
//       │       ─┴─       │
//       │                 │
//       └───┐         ┌───┘
//           │         │
//           │         │
//           │         │
//           │         └──────────────┐
//           │                        │
//           │                        ├─┐
//           │                        ┌─┘    
//           │                        │
//           └─┐  ┐  ┌───────┬──┐  ┌──┘         
//             │ ─┤ ─┤       │ ─┤ ─┤         
//             └──┴──┘       └──┴──┘ 
withClock(io.clk){

    val cvt_sat_add_ext = io.cvt_saturation_add
    val cvt_sat_sub_ext = Cat(Fill(4, false.B), io.cvt_saturation_sub)
    val cvt_sat_inc = cvt_sat_add_ext > cvt_sat_sub_ext
    val cvt_sat_dec = cvt_sat_add_ext < cvt_sat_sub_ext
    val cvt_sat_mod_ext = Mux(cvt_sat_inc, (cvt_sat_add_ext -& cvt_sat_sub_ext) , (cvt_sat_sub_ext -& cvt_sat_add_ext))(4, 0)

    val cvt_sat_sub_guard = io.cvt_saturation_cnt(31,1).orR === false.B
    val cvt_sat_sub_act = io.cvt_saturation_cnt(0)
    val cvt_sat_sub_act_ext = Cat(Fill(4, false.B), cvt_sat_sub_act)
    val cvt_sat_sub_flow = cvt_sat_dec & cvt_sat_sub_guard & (cvt_sat_sub_act_ext < cvt_sat_mod_ext)

    val cvt_sat_add_guard = io.cvt_saturation_cnt(31,5).andR === true.B
    val cvt_sat_add_act = io.cvt_saturation_cnt(4,0)
    val cvt_sat_add_act_ext = cvt_sat_add_act
    val cvt_sat_add_flow = cvt_sat_inc & cvt_sat_add_guard & ((cvt_sat_add_act_ext +& cvt_sat_mod_ext) > 31.U )

    val i_add = Mux(cvt_sat_add_flow, (31.U -& cvt_sat_add_act), Mux(cvt_sat_sub_flow, 0.U, io.cvt_saturation_add))
    val i_sub = Mux(cvt_sat_sub_flow, cvt_sat_sub_act, Mux(cvt_sat_add_flow, 0.U, io.cvt_saturation_sub))

    val cvt_sat_cvt_sat_adv = i_add =/= Cat(Fill(4,false.B), i_sub(0))

    val cvt_sat_cvt_sat_cnt_cur = RegInit(0.U(32.W))
    val cvt_sat_cvt_sat_cnt_ext = Cat(false.B, false.B, cvt_sat_cvt_sat_cnt_cur)
    val cvt_sat_cvt_sat_cnt_mod = cvt_sat_cvt_sat_cnt_cur +& i_add(4, 0) -& i_sub(0)
    val cvt_sat_cvt_sat_cnt_new = Mux(cvt_sat_cvt_sat_adv, cvt_sat_cvt_sat_cnt_mod, cvt_sat_cvt_sat_cnt_ext)
    val cvt_sat_cvt_sat_cnt_nxt = Mux(io.cvt_saturation_clr, 0.U, cvt_sat_cvt_sat_cnt_new)

    when(io.cvt_saturation_cen){
        cvt_sat_cvt_sat_cnt_cur := cvt_sat_cvt_sat_cnt_nxt(31,0)
    }

    io.cvt_saturation_cnt := cvt_sat_cvt_sat_cnt_cur
}}
