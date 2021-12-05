package edu.kit.compiler.transform


import firm.ClassType
import firm.Entity
import firm.MethodType
import firm.Program
import firm.Util

/**
 *  An adaptation of jFirms's Lower.java.
 *
 *  From Lower.java:
 *   A transformation pass that lowers some highlevel features of OO languages,
 *   so we can generate machine assembler:
 *
 *   - Move methods from classtypes into global type
 *   - escape/replace special chars in LdNames so normal assemblers are fine
 *     with them
 */

class Lower private constructor() {
    private fun fixEntityLdName(entity: Entity) {
        var name = entity.ldName
        /* replace some "Java" names with "C" names:
		 * - The main method has to be called "main" in C
		 * - There is no PrintStream.println (but we use our dummy print_int implementation)
		 * - java/lang/system/out is also replaced by a dummy variable
		 */
        if (name == "main([Ljava.lang.String;)V") {
            name = "main"
        }
        if (name == "java/io/PrintStream/println(I)V") {
            name = "print_int"
        }
        if (name == "java/lang/System/out") {
            name = "sysoutdummy"
        }
        /* C linker doesn't allow all possible ascii chars for identifiers,
		 * filter some out */
        name = name.replace("[()\\[\\];]".toRegex(), "_")
        //println("der name ist $name")
        entity.setLdIdent(name)
    }
//    private fun lowerMethods(typeRegistry: TypeRegistry) {
//        typeRegistry.classes.forEach() {
//            it.value.methods.forEach() {
//                it.value.owner = Program.getGlobalType()
//            }
//        }
//    }
    private fun fixEntityNames() {
        for (entity in Program.getGlobalType().members) {
            fixEntityLdName(entity)
        }
    }

    private fun layoutClass(cls: ClassType) {
        //println(cls.toString())
        if (cls == Program.getGlobalType()) return
        var m = 0
        while (m < cls.nMembers /* nothing */) {
            val member = cls.getMember(m)
            val type = member.type
            if (type !is MethodType) {
                ++m
                continue
            }

            /* methods get implemented outside the class, move the entity */
            member.owner = Program.getGlobalType()
        }
        //cls.layoutFields()
    }

    private fun layoutTypes() {
        for (type in Program.getTypes()) {
            //println(type.toString())
            //println(" -------------")
        }
        for (type in Program.getTypes()) {
            //println(type.toString())
            //println(type is ClassType)
            if (type is ClassType) {
                layoutClass(type)
            }
            //println("type to string")
            //type.finishLayout()
            //println("---")
        }
    }

    companion object {
        /**
         * Lower some highlevel constructs to make firm-graph suitable to be used
         * by x86 backend.
         */
        fun lower() {
            val instance = Lower()
            instance.layoutTypes()
            //instance.fixEntityNames()
            Util.lowerSels()
        }
//        fun lowerMethods(typeRegistry: TypeRegistry) {
//            val instance = Lower()
//            typeRegistry.classes.forEach() {
//                it.value.methods.forEach() {
//                    it.value.owner = Program.getGlobalType()
//                }
//            }
//        }
    }
}
