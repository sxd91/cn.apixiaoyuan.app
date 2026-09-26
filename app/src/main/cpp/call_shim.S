// call_shim.S —— 精确控制寄存器的调用垫片
// 目标函数（设备 so 偏移 +0x4078，即 JNI_OnLoad+0x4078）签名：
//   std::string chain(sret=x8, x1=const std::string&, x2=const std::string&, w3=int)
// libc++ std::string 布局：短串 [0]=(len<<1)，长串 [0]=cap|1 / [8]=len / [16]=ptr
//
// call_shim(fn=x0, out=x1, a=x2, b=x3, c=w4)
.global call_shim
.type call_shim, %function
call_shim:
    stp x29, x30, [sp, #-16]!
    mov x9, x0          // fn
    mov x8, x1          // sret out
    mov x1, x2          // a
    mov x2, x3          // b
    mov w3, w4          // c
    mov x0, #0
    blr x9
    ldp x29, x30, [sp], #16
    ret
