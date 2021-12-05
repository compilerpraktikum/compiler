# Optimierung

## Konstantenfaltung extended (eigentlich Faltung+Konstantenpropagation)

* Annotationen aus Verband L mit **⊥** als Infimum und **⊤** als Supremum aller Elemente
implementiert werden*
* Algorithmus (Datenflussanalyse):
    1. Für den Verband *TargetValue* verwenden
        * **⊥**=*TargetValue.getUnknown()*
        * **⊤**=*TargetValue.getBad()*
        * comparing?
    2. Worklist-Algorithmus (Sitzung 8, Folie 15)
        * Initialisierung:
            * worklist := Stack<Node.>(all_nodes)     (Erstmal wurst, ob Stack oder whatever)
            * annotations := Map<Node, TargetValue>()
        * Transferfunktionen der einzelnen Knotentypen
            * $f_\phi(I_{v_0}, I_{v_1}) = I_{v_0} \sqcup I_{v_1} = Supremum(I_{v_0}, I_{v_1})$ *(VL.05 F.504)*
            * $f_+(I_{v_0}, I_{v_1})$   | $ $   | $Fall$
                ---:                    | :---: |:---
                $⊥$                     | $ $   | $(I_{v_0} = ⊥ ∨ I_{v_1} = ⊥)$
                $c_0 + c_1 $            | $ $   | $(I_{v_0} = c_0 ∧ I_{v_1} = c_1)$
                $⊤$                     | $ $   | $sonst$
              *(Sitzung 8, Folie 11)*
            * $f_-(I_{v_0}, I_{v_1}) = $ analog zu $f_+$
            * $f_*(I_{v_0}, I_{v_1}) = $ analog zu $f_+$
            * $f_/(I_{v_0}, I_{v_1}) = $ analog zu $f_+$
            * $f_{Return}(I_v) = I_v $
            * $f_{Proj}$ ?
            * Eigenschaften VL.05 F.568

    3. Anwendung der gefundenen Transformation (Annotations-Map (Node, TargetValue))

### TODO

* Spezialfälle: Nur teilweise konstante Operanden. Bsp.:
    * x > MAX_INT <==> false
    * x < MIN_INT <==> false
    * true && x <==> x && true <==> true
    * ...
    * OBACHT: Seiteneffekte, falls x ein Assignment!
* Error Handling (eig sollten ab hier alle Fehler Runtime-Fehler sein):
    * Overflows?
    * Div durch 0?