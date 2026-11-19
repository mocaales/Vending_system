# Arhitektura (načrtovano)

Kako sistem deluje. Izhodišče: [skupina_10.pdf](skupina_10.pdf). Kdo kaj dela: [02-razdelitev-dela.md](02-razdelitev-dela.md). Končne točke: [03-api-pogodbe.md](03-api-pogodbe.md). Oznake B1, B3–B6 so odprte odločitve ([02](02-razdelitev-dela.md#odprte-odločitve)).

## 1. Komponente

```drawio
<mxfile>
  <diagram id="K4Z3dZy-faPiisyOEC-r" name="Page-1">
    <mxGraphModel dx="1739" dy="994" grid="1" gridSize="10" guides="1" tooltips="0" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="827" pageHeight="1169" math="0" shadow="0">
      <root>
        <mxCell id="tSrmnOXPhnbIp_Sv3z0b-0" />
        <mxCell id="tSrmnOXPhnbIp_Sv3z0b-1" parent="tSrmnOXPhnbIp_Sv3z0b-0" />
        <UserObject label="" mermaidData="{&#xa;  &quot;data&quot;: &quot;flowchart LR\n  OP[\&quot;Operater (brskalnik)\&quot;] --&gt; UI[Web UI]\n  KU[\&quot;Kupec (javna stran)\&quot;] --&gt; UI\n  UI --&gt; GW[\&quot;Gateway + Frontend API\&quot;]\n  SIM[Simulator] --&gt; GW\n  GW --&gt; MA[Machine]\n  GW --&gt; MO[Monitoring]\n  GW --&gt; PR[Product]\n  GW --&gt; IN[Inventory]\n  GW --&gt; SA[Sales]\n  MA --&gt; IN\n  MO --&gt; MA\n  MO --&gt; PR\n  PR --&gt; SA\n  SA --&gt; PR\n  SA --&gt; IN\n  IN --&gt; MA\n  IN --&gt; PR\n  IN --&gt; MO\n  MO -. SMTP .-&gt; MAIL[Mailpit]\n  GW --- GWDB[(gateway-db)]\n  MA --- MADB[(machine-db)]\n  MO --- MODB[(monitoring-db)]\n  PR --- PRDB[(product-db)]\n  IN --- INDB[(inventory-db)]\n  SA --- SADB[(sales-db)]\n  classDef ales fill:#E8F1FB,stroke:#3B6EA8\n  classDef nejc fill:#EAF6EA,stroke:#3C8C3C\n  classDef darko fill:#FDF1E3,stroke:#B9772E\n  class MA,MO,SA,SIM ales\n  class PR,IN nejc\n  class GW,UI darko&quot;,&#xa;  &quot;config&quot;: null&#xa;}" id="58CDRFGWXw-fFxP_Z7ij-0">
          <mxCell connectable="0" parent="tSrmnOXPhnbIp_Sv3z0b-1" style="group;transparentBounds=1;editIcon=1;lockedGroup=0;groupPadding=10;" vertex="1">
            <mxGeometry as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Operater (brskalnik)" mermaidId="n:OP" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Operater (brskalnik)" id="tSrmnOXPhnbIp_Sv3z0b-2">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="205" x="10" y="440" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Web UI" mermaidId="n:UI" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#FDF1E3;strokeColor=#B9772E;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Web UI" id="tSrmnOXPhnbIp_Sv3z0b-3">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#FDF1E3;strokeColor=#B9772E;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="110" x="275" y="415" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Kupec (javna stran)" mermaidId="n:KU" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Kupec (javna stran)" id="tSrmnOXPhnbIp_Sv3z0b-4">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="200" x="13" y="336" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Gateway + Frontend API" mermaidId="n:GW" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#FDF1E3;strokeColor=#B9772E;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Gateway + Frontend API" id="tSrmnOXPhnbIp_Sv3z0b-5">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#FDF1E3;strokeColor=#B9772E;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="232" x="444" y="363" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Simulator" mermaidId="n:SIM" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#E8F1FB;strokeColor=#3B6EA8;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Simulator" id="tSrmnOXPhnbIp_Sv3z0b-6">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#E8F1FB;strokeColor=#3B6EA8;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="129" x="265" y="311" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Machine" mermaidId="n:MA" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#E8F1FB;strokeColor=#3B6EA8;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Machine" id="tSrmnOXPhnbIp_Sv3z0b-7">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#E8F1FB;strokeColor=#3B6EA8;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="118" x="726" y="160" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Monitoring" mermaidId="n:MO" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#E8F1FB;strokeColor=#3B6EA8;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Monitoring" id="tSrmnOXPhnbIp_Sv3z0b-8">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#E8F1FB;strokeColor=#3B6EA8;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="136" x="1072" y="251" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Product" mermaidId="n:PR" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#EAF6EA;strokeColor=#3C8C3C;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Product" id="tSrmnOXPhnbIp_Sv3z0b-9">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#EAF6EA;strokeColor=#3C8C3C;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="115" x="1296" y="470" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Inventory" mermaidId="n:IN" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#EAF6EA;strokeColor=#3C8C3C;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Inventory" id="tSrmnOXPhnbIp_Sv3z0b-10">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#EAF6EA;strokeColor=#3C8C3C;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="128" x="894" y="253" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Sales" mermaidId="n:SA" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#E8F1FB;strokeColor=#3B6EA8;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Sales" id="tSrmnOXPhnbIp_Sv3z0b-11">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=#E8F1FB;strokeColor=#3B6EA8;fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="96" x="1461" y="481" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Mailpit" mermaidId="n:MAIL" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Mailpit" id="tSrmnOXPhnbIp_Sv3z0b-12">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="109" x="1299" y="48" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="gateway-db" mermaidId="n:GWDB" mermaidBaseStyle="html=1;shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;strokeWidth=1;whiteSpace=wrap;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="gateway-db" id="tSrmnOXPhnbIp_Sv3z0b-13">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;strokeWidth=1;whiteSpace=wrap;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="67" width="98" x="736" y="345" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="machine-db" mermaidId="n:MADB" mermaidBaseStyle="html=1;shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;strokeWidth=1;whiteSpace=wrap;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="machine-db" id="tSrmnOXPhnbIp_Sv3z0b-14">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;strokeWidth=1;whiteSpace=wrap;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="67" width="99" x="909" y="134" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="monitoring-db" mermaidId="n:MODB" mermaidBaseStyle="html=1;shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;strokeWidth=1;whiteSpace=wrap;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="monitoring-db" id="tSrmnOXPhnbIp_Sv3z0b-15">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;strokeWidth=1;whiteSpace=wrap;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="67" width="116" x="1295" y="268" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="product-db" mermaidId="n:PRDB" mermaidBaseStyle="html=1;shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;strokeWidth=1;whiteSpace=wrap;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="product-db" id="tSrmnOXPhnbIp_Sv3z0b-16">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;strokeWidth=1;whiteSpace=wrap;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="67" width="94" x="1462" y="585" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="inventory-db" mermaidId="n:INDB" mermaidBaseStyle="html=1;shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;strokeWidth=1;whiteSpace=wrap;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="inventory-db" id="tSrmnOXPhnbIp_Sv3z0b-17">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;strokeWidth=1;whiteSpace=wrap;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="67" width="106" x="1087" y="45" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="sales-db" mermaidId="n:SADB" mermaidBaseStyle="html=1;shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;strokeWidth=1;whiteSpace=wrap;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="sales-db" id="tSrmnOXPhnbIp_Sv3z0b-18">
          <mxCell parent="58CDRFGWXw-fFxP_Z7ij-0" style="html=1;shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=10;strokeWidth=1;whiteSpace=wrap;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="67" width="74" x="1607" y="474" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:OP-&gt;UI#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0;entryY=0.78;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-19">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-2" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0;entryY=0.78;" target="tSrmnOXPhnbIp_Sv3z0b-3">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="240" y="467" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:KU-&gt;UI#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0.22;entryY=0;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-20">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-4" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0.22;entryY=0;" target="tSrmnOXPhnbIp_Sv3z0b-3">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="240" y="363" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:UI-&gt;GW#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0.19;entryY=1;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-21">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-3" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0.19;entryY=1;" target="tSrmnOXPhnbIp_Sv3z0b-5">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="419" y="442" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:SIM-&gt;GW#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0.19;entryY=0;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-22">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-6" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0.19;entryY=0;" target="tSrmnOXPhnbIp_Sv3z0b-5">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="419" y="338" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:GW-&gt;MA#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.58;exitY=0;entryX=0;entryY=0.5;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-23">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-5" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.58;exitY=0;entryX=0;entryY=0.5;" target="tSrmnOXPhnbIp_Sv3z0b-7">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="701" y="187" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:GW-&gt;MO#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.72;exitY=1;entryX=0.4;entryY=1;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-24">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-5" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.72;exitY=1;entryX=0.4;entryY=1;" target="tSrmnOXPhnbIp_Sv3z0b-8">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="701" y="467" />
                <mxPoint x="785" y="467" />
                <mxPoint x="869" y="467" />
                <mxPoint x="958" y="467" />
                <mxPoint x="1047" y="467" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:GW-&gt;PR#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.62;exitY=1;entryX=0;entryY=0.81;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-25">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-5" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.62;exitY=1;entryX=0;entryY=0.81;" target="tSrmnOXPhnbIp_Sv3z0b-9">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="701" y="528" />
                <mxPoint x="785" y="528" />
                <mxPoint x="869" y="528" />
                <mxPoint x="958" y="528" />
                <mxPoint x="1047" y="528" />
                <mxPoint x="1140" y="528" />
                <mxPoint x="1252" y="528" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:GW-&gt;IN#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.79;exitY=1;entryX=0.39;entryY=1;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-26">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-5" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.79;exitY=1;entryX=0.39;entryY=1;" target="tSrmnOXPhnbIp_Sv3z0b-10">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="701" y="447" />
                <mxPoint x="785" y="447" />
                <mxPoint x="869" y="447" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:GW-&gt;SA#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.59;exitY=1;entryX=0.19;entryY=1;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-27">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-5" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.59;exitY=1;entryX=0.19;entryY=1;" target="tSrmnOXPhnbIp_Sv3z0b-11">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="701" y="573" />
                <mxPoint x="785" y="573" />
                <mxPoint x="869" y="573" />
                <mxPoint x="958" y="573" />
                <mxPoint x="1047" y="573" />
                <mxPoint x="1140" y="573" />
                <mxPoint x="1252" y="573" />
                <mxPoint x="1353" y="573" />
                <mxPoint x="1436" y="573" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:MA-&gt;IN#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.6111111111111112;entryX=0;entryY=0.6111111111111112;curved=1;exitX=1;exitY=0.61;entryX=0;entryY=0.61;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-28">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-7" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.61;entryX=0;entryY=0.61;" target="tSrmnOXPhnbIp_Sv3z0b-10">
            <mxGeometry relative="1" as="geometry">
              <mxPoint as="offset" />
              <Array as="points">
                <mxPoint x="869" y="246" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:MO-&gt;MA#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.4;exitY=0;entryX=0.72;entryY=0;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-29">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-8" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.4;exitY=0;entryX=0.72;entryY=0;" target="tSrmnOXPhnbIp_Sv3z0b-7">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1047" y="99" />
                <mxPoint x="958" y="99" />
                <mxPoint x="869" y="99" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:MO-&gt;PR#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.6;exitY=1;entryX=0;entryY=0.61;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-30">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-8" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.6;exitY=1;entryX=0;entryY=0.61;" target="tSrmnOXPhnbIp_Sv3z0b-9">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1252" y="508" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:PR-&gt;SA#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.6111111111111112;entryX=0;entryY=0.6111111111111112;curved=1;exitX=1;exitY=0.61;entryX=0;entryY=0.61;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-31">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-9" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.61;entryX=0;entryY=0.61;" target="tSrmnOXPhnbIp_Sv3z0b-11">
            <mxGeometry relative="1" as="geometry">
              <mxPoint as="offset" />
              <Array as="points">
                <mxPoint x="1436" y="515" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:SA-&gt;PR#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0;exitY=0.3888888888888889;entryX=1;entryY=0.3888888888888889;curved=1;exitX=0;exitY=0.39;entryX=1;entryY=0.39;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-32">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-11" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0;exitY=0.39;entryX=1;entryY=0.39;" target="tSrmnOXPhnbIp_Sv3z0b-9">
            <mxGeometry relative="1" as="geometry">
              <mxPoint as="offset" />
              <Array as="points">
                <mxPoint x="1436" y="491" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:SA-&gt;IN#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.46;exitY=0;entryX=0.57;entryY=0;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-33">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-11" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.46;exitY=0;entryX=0.57;entryY=0;" target="tSrmnOXPhnbIp_Sv3z0b-10">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1436" y="10" />
                <mxPoint x="1353" y="10" />
                <mxPoint x="1252" y="10" />
                <mxPoint x="1140" y="10" />
                <mxPoint x="1047" y="10" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:IN-&gt;MA#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0;exitY=0.3888888888888889;entryX=1;entryY=0.3888888888888889;curved=1;exitX=0;exitY=0.39;entryX=1;entryY=0.39;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-34">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-10" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0;exitY=0.39;entryX=1;entryY=0.39;" target="tSrmnOXPhnbIp_Sv3z0b-7">
            <mxGeometry relative="1" as="geometry">
              <mxPoint as="offset" />
              <Array as="points">
                <mxPoint x="869" y="222" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:IN-&gt;PR#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.59;exitY=1;entryX=0;entryY=0.39;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-35">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-10" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.59;exitY=1;entryX=0;entryY=0.39;" target="tSrmnOXPhnbIp_Sv3z0b-9">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1047" y="487" />
                <mxPoint x="1140" y="487" />
                <mxPoint x="1252" y="487" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:IN-&gt;MO#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0;entryY=0.52;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-36">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-10" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0;entryY=0.52;" target="tSrmnOXPhnbIp_Sv3z0b-8">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1047" y="280" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="SMTP" mermaidId="e:MO-&gt;MAIL#0" mermaidBaseStyle="curved=1;dashed=1;dashPattern=2 3;fixDash=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);html=1;fontSize=16;labelBackgroundColor=light-dark(#E8E8E88D,#2a2a2a8D);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontColor=light-dark(#333333,#cccccc);exitX=0.61;exitY=0;entryX=0;entryY=0.5;" mermaidBaseValue="SMTP" id="tSrmnOXPhnbIp_Sv3z0b-37">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-8" style="curved=1;dashed=1;dashPattern=2 3;fixDash=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);html=1;fontSize=16;labelBackgroundColor=light-dark(#E8E8E88D,#2a2a2a8D);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontColor=light-dark(#333333,#cccccc);exitX=0.61;exitY=0;entryX=0;entryY=0.5;" target="tSrmnOXPhnbIp_Sv3z0b-12">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1252" y="75" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:GW-&gt;GWDB#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=none;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.31;entryX=0;entryY=0.49;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-38">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-5" style="curved=1;startArrow=none;endArrow=none;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.31;entryX=0;entryY=0.49;" target="tSrmnOXPhnbIp_Sv3z0b-13">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="701" y="378" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:MA-&gt;MADB#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=none;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.24;entryX=0;entryY=0.49;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-39">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-7" style="curved=1;startArrow=none;endArrow=none;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.24;entryX=0;entryY=0.49;" target="tSrmnOXPhnbIp_Sv3z0b-14">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="869" y="167" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:MO-&gt;MODB#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=none;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.76;entryX=0;entryY=0.51;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-40">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-8" style="curved=1;startArrow=none;endArrow=none;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.76;entryX=0;entryY=0.51;" target="tSrmnOXPhnbIp_Sv3z0b-15">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1252" y="302" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:PR-&gt;PRDB#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=none;strokeColor=light-dark(#333333,#cccccc);exitX=0.65;exitY=1;entryX=0;entryY=0.49;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-41">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-9" style="curved=1;startArrow=none;endArrow=none;strokeColor=light-dark(#333333,#cccccc);exitX=0.65;exitY=1;entryX=0;entryY=0.49;" target="tSrmnOXPhnbIp_Sv3z0b-16">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1436" y="618" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:IN-&gt;INDB#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=none;strokeColor=light-dark(#333333,#cccccc);exitX=0.59;exitY=0;entryX=0;entryY=0.51;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-42">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-10" style="curved=1;startArrow=none;endArrow=none;strokeColor=light-dark(#333333,#cccccc);exitX=0.59;exitY=0;entryX=0;entryY=0.51;" target="tSrmnOXPhnbIp_Sv3z0b-17">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1047" y="79" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:SA-&gt;SADB#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=none;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0;entryY=0.51;" mermaidBaseValue="" id="tSrmnOXPhnbIp_Sv3z0b-43">
          <mxCell edge="1" parent="58CDRFGWXw-fFxP_Z7ij-0" source="tSrmnOXPhnbIp_Sv3z0b-11" style="curved=1;startArrow=none;endArrow=none;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0;entryY=0.51;" target="tSrmnOXPhnbIp_Sv3z0b-18">
            <mxGeometry relative="1" as="geometry">
              <Array as="points" />
            </mxGeometry>
          </mxCell>
        </UserObject>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>

```

Pet storitev in gateway ima vsak svojo bazo PostgreSQL v svojem zabojniku in svoje migracije. Gateway v svoji bazi hrani samo račune operaterjev. Puščica je klic REST (sinhron ali prek outboxa, §3). Barve: modro Aleš, zeleno Nejc, oranžno Darko.

| Komponenta | Lastnik | Tehnologija | Skrbi za |
|---|---|---|---|
| Machine | Aleš | Spring Boot | avtomati, predali, administrativno stanje, ključi avtomatov |
| Monitoring | Aleš | Spring Boot | meritve, dosegljivost, alarmi, pragovi, e-pošta |
| Sales | Aleš | Spring Boot | nespremenljiva zgodovina prodaj, statistika, uskladitev, dnevni zaključki |
| Product | Nejc | FastAPI | katalog izdelkov in kategorije |
| Inventory | Nejc | FastAPI | količina in serija v predalu, polnitve, praznjenje in odpis potekle zaloge, premiki |
| Gateway + Frontend API | Darko | Express | prijava in računi operaterjev, preverjanje identitete, usmerjanje, sestavljeni pogledi |
| Web UI | Darko | React | javni zemljevid, operaterska plošča |
| Simulator | Aleš (B1) | Python | emulira avtomate: meritve in prodaje |

## 2. Načela

- Vsaka komponenta ima svojo bazo. Podatki druge komponente so dostopni samo prek njenega REST API-ja; sklici so ID-ji, tujih ključev med bazami ni.
- Zunanji odjemalci (Web UI, simulator) dostopajo samo prek gatewaya; storitve in baze nimajo vrat na računalniku.
- Na zahtevo druge storitve storitev odgovori iz svojih podatkov, brez nadaljnjih sinhronih klicev. Najdaljša veriga je gateway → storitev → storitev.
- Preverjanje pred spremembo (umik avtomata, arhiviranje izdelka) ob nedosegljivi odvisnosti spremembo zavrne z 503.
- Sales sporoča Inventory prek outboxa (trajen zapis v isti transakciji, delavec ga pošlje); posrednika sporočil ni.
- Nedosegljiv Monitoring ali SMTP nikoli ne razveljavi spremembe zaloge ali prodaje.
- Poslovna pravila veljajo na strežniku, ne samo v brskalniku.
- Poti: storitve `/v1/...`, gateway `/api/v1/...`.

## 3. Notranji klici

| Kliče | Klicani | Namen | Če klicani ne odgovori |
|---|---|---|---|
| Gateway (Darko) | Machine (Aleš) | preverjanje ključa avtomata | 503, naprava ponovi |
| Gateway, Frontend API (Darko) | vseh pet storitev | prehodne poti, sestavljeni pogledi | prehodna pot 503 ali 504; odsek pogleda `UNAVAILABLE` |
| Machine (Aleš) | Inventory (Nejc) | umik samo brez zaloge (409 `MACHINE_HAS_STOCK`) | 503, avtomat ni umaknjen |
| Monitoring (Aleš) | Product (Nejc) | nazivi izdelkov v e-pošti `LOW_STOCK` | e-pošta z ID-ji izdelkov |
| Monitoring (Aleš) | Machine (Aleš) | aktivni avtomati za `OFFLINE`, naziv avtomata v e-pošti | preverjanje se ponovi |
| Monitoring (outbox) | SMTP | e-pošta ob alarmu | ponavlja; alarm se ne spremeni |
| Product (Nejc) | Sales (Aleš) | arhiviranje samo brez odprte uskladitve (409 `PRODUCT_HAS_OPEN_RECONCILIATION`) | 503, izdelek ostane aktiven |
| Inventory (Nejc) | Machine (Aleš) | avtomat in predal obstajata, avtomat ni umaknjen | 503, nič se ne shrani |
| Inventory (Nejc) | Product (Nejc) | izdelek obstaja in je aktiven | 503, nič se ne shrani |
| Inventory (outbox) | Monitoring (Aleš) | alarm `LOW_STOCK` | ostane v outboxu, ponavlja |
| Sales (Aleš) | Product, Inventory (Nejc) | ob sprejemu prodaje preveri izdelek in predal | 503, nič se ne shrani, simulator ponovi |
| Sales (outbox) | Inventory (Nejc) | enkratno zmanjšanje zaloge po `saleId` | `PENDING`, ponavlja |
| Simulator (Aleš) | Gateway (Darko) | meritve in prodaje | dogodek hrani in ponavlja |

Vsaka komponenta kliče vsaj eno komponento drugega člana: Machine, Monitoring in Sales kličejo Nejčeve storitve, Product in Inventory Aleševe, gateway pa vse.

## 4. Lastništvo podatkov in enumi

| Komponenta | Podatki | Enumi |
|---|---|---|
| Gateway | računi operaterjev | račun: `ACTIVE`, `DEACTIVATED` |
| Machine | avtomati, predali (`slotCode`), ključi avtomatov | status: `ACTIVE`, `SERVICE`, `RETIRED`; ključ: `ACTIVE`, `REVOKED` |
| Monitoring | meritve, alarmi, pragovi, e-poštni outbox | alarm: `ACTIVE`, `RESOLVED`; razrešitev: `AUTO`, `MANUAL`; tip: `TEMPERATURE_HIGH`, `DOOR_OPEN`, `OFFLINE`, `LOW_STOCK` |
| Product | izdelki, kategorije | `ACTIVE`, `ARCHIVED` |
| Inventory | zaloga predala, serije, premiki, outbox nizke zaloge | premik: `SET`, `REFILL`, `REMOVAL`, `SALE`; rezultat: `APPLIED`, `NEEDS_RECONCILIATION` |
| Sales | prodaje, dnevni zaključki, outbox zmanjšanj | uskladitev: `PENDING`, `APPLIED`, `NEEDS_RECONCILIATION`, `RESOLVED`; zaključek: `DRAFT`, `CONFIRMED` |

Denar je decimalen, časi so UTC. Podatki prodaje se po sprejemu ne spreminjajo; spremeni se samo stanje uskladitve.

## 5. Ključni tokovi

### 5.1 Prodaja

```drawio
<mxfile>
  <diagram id="GwWC9VzCobGrpW2nao9b" name="Page-1">
    <mxGraphModel dx="1362" dy="1082" grid="1" gridSize="10" guides="1" tooltips="0" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="827" pageHeight="1169" math="0" shadow="0">
      <root>
        <mxCell id="O1xT6FS1MFyyzcLnytJf-0" />
        <mxCell id="O1xT6FS1MFyyzcLnytJf-1" parent="O1xT6FS1MFyyzcLnytJf-0" />
        <UserObject label="" mermaidData="{&#xa;  &quot;data&quot;: &quot;sequenceDiagram\n  participant S as Simulator\n  participant G as Gateway\n  participant SA as Sales\n  participant I as Inventory\n  participant M as Monitoring\n  S-&gt;&gt;G: POST /api/v1/device/sales (ključ avtomata)\n  G-&gt;&gt;SA: POST /v1/sales\n  SA-&gt;&gt;SA: preveri izdelek in predal, shrani prodajo + outbox\n  SA--&gt;&gt;S: 201 (ponovitev 200, drugačno telo 409)\n  SA-&gt;&gt;I: delavec: decrease po saleId\n  I-&gt;&gt;I: premik enkrat + outbox nizke zaloge\n  I-&gt;&gt;M: delavec: POST /v1/alerts LOW_STOCK\n  M-&gt;&gt;M: alarm + e-poštni outbox&quot;,&#xa;  &quot;config&quot;: null&#xa;}" id="qIzsALQXbfJu2t_9ep7G-0">
          <mxCell connectable="0" parent="O1xT6FS1MFyyzcLnytJf-1" style="group;transparentBounds=1;editIcon=1;lockedGroup=0;groupPadding=10;" vertex="1">
            <mxGeometry as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Simulator" mermaidId="n:S" mermaidBaseStyle="html=1;shape=umlLifeline;perimeter=lifelinePerimeter;whiteSpace=wrap;container=1;dropTarget=0;collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;newEdgeStyle={&quot;edgeStyle&quot;:&quot;sequenceEdgeStyle&quot;,&quot;libavoidRouting&quot;:0,&quot;curved&quot;:0,&quot;rounded&quot;:0};lifelineDashed=0;strokeWidth=2;rounded=1;absoluteArcSize=1;arcSize=6;lifelineColor=light-dark(#9370DB,#cccccc);size=65;lifelineMirror=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Simulator" id="O1xT6FS1MFyyzcLnytJf-2">
          <mxCell parent="qIzsALQXbfJu2t_9ep7G-0" style="html=1;shape=umlLifeline;perimeter=lifelinePerimeter;whiteSpace=wrap;container=1;dropTarget=0;collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;newEdgeStyle={&quot;edgeStyle&quot;:&quot;sequenceEdgeStyle&quot;,&quot;libavoidRouting&quot;:0,&quot;curved&quot;:0,&quot;rounded&quot;:0};lifelineDashed=0;strokeWidth=2;rounded=1;absoluteArcSize=1;arcSize=6;lifelineColor=light-dark(#9370DB,#cccccc);size=65;lifelineMirror=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="608" width="150" x="10" y="10" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Gateway" mermaidId="n:G" mermaidBaseStyle="html=1;shape=umlLifeline;perimeter=lifelinePerimeter;whiteSpace=wrap;container=1;dropTarget=0;collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;newEdgeStyle={&quot;edgeStyle&quot;:&quot;sequenceEdgeStyle&quot;,&quot;libavoidRouting&quot;:0,&quot;curved&quot;:0,&quot;rounded&quot;:0};lifelineDashed=0;strokeWidth=2;rounded=1;absoluteArcSize=1;arcSize=6;lifelineColor=light-dark(#9370DB,#cccccc);size=65;lifelineMirror=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Gateway" id="O1xT6FS1MFyyzcLnytJf-3">
          <mxCell parent="qIzsALQXbfJu2t_9ep7G-0" style="html=1;shape=umlLifeline;perimeter=lifelinePerimeter;whiteSpace=wrap;container=1;dropTarget=0;collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;newEdgeStyle={&quot;edgeStyle&quot;:&quot;sequenceEdgeStyle&quot;,&quot;libavoidRouting&quot;:0,&quot;curved&quot;:0,&quot;rounded&quot;:0};lifelineDashed=0;strokeWidth=2;rounded=1;absoluteArcSize=1;arcSize=6;lifelineColor=light-dark(#9370DB,#cccccc);size=65;lifelineMirror=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="608" width="150" x="379" y="10" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Sales" mermaidId="n:SA" mermaidBaseStyle="html=1;shape=umlLifeline;perimeter=lifelinePerimeter;whiteSpace=wrap;container=1;dropTarget=0;collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;newEdgeStyle={&quot;edgeStyle&quot;:&quot;sequenceEdgeStyle&quot;,&quot;libavoidRouting&quot;:0,&quot;curved&quot;:0,&quot;rounded&quot;:0};lifelineDashed=0;strokeWidth=2;rounded=1;absoluteArcSize=1;arcSize=6;lifelineColor=light-dark(#9370DB,#cccccc);size=65;lifelineMirror=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Sales" id="O1xT6FS1MFyyzcLnytJf-4">
          <mxCell parent="qIzsALQXbfJu2t_9ep7G-0" style="html=1;shape=umlLifeline;perimeter=lifelinePerimeter;whiteSpace=wrap;container=1;dropTarget=0;collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;newEdgeStyle={&quot;edgeStyle&quot;:&quot;sequenceEdgeStyle&quot;,&quot;libavoidRouting&quot;:0,&quot;curved&quot;:0,&quot;rounded&quot;:0};lifelineDashed=0;strokeWidth=2;rounded=1;absoluteArcSize=1;arcSize=6;lifelineColor=light-dark(#9370DB,#cccccc);size=65;lifelineMirror=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="608" width="150" x="579" y="10" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Inventory" mermaidId="n:I" mermaidBaseStyle="html=1;shape=umlLifeline;perimeter=lifelinePerimeter;whiteSpace=wrap;container=1;dropTarget=0;collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;newEdgeStyle={&quot;edgeStyle&quot;:&quot;sequenceEdgeStyle&quot;,&quot;libavoidRouting&quot;:0,&quot;curved&quot;:0,&quot;rounded&quot;:0};lifelineDashed=0;strokeWidth=2;rounded=1;absoluteArcSize=1;arcSize=6;lifelineColor=light-dark(#9370DB,#cccccc);size=65;lifelineMirror=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Inventory" id="O1xT6FS1MFyyzcLnytJf-5">
          <mxCell parent="qIzsALQXbfJu2t_9ep7G-0" style="html=1;shape=umlLifeline;perimeter=lifelinePerimeter;whiteSpace=wrap;container=1;dropTarget=0;collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;newEdgeStyle={&quot;edgeStyle&quot;:&quot;sequenceEdgeStyle&quot;,&quot;libavoidRouting&quot;:0,&quot;curved&quot;:0,&quot;rounded&quot;:0};lifelineDashed=0;strokeWidth=2;rounded=1;absoluteArcSize=1;arcSize=6;lifelineColor=light-dark(#9370DB,#cccccc);size=65;lifelineMirror=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="608" width="150" x="828" y="10" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Monitoring" mermaidId="n:M" mermaidBaseStyle="html=1;shape=umlLifeline;perimeter=lifelinePerimeter;whiteSpace=wrap;container=1;dropTarget=0;collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;newEdgeStyle={&quot;edgeStyle&quot;:&quot;sequenceEdgeStyle&quot;,&quot;libavoidRouting&quot;:0,&quot;curved&quot;:0,&quot;rounded&quot;:0};lifelineDashed=0;strokeWidth=2;rounded=1;absoluteArcSize=1;arcSize=6;lifelineColor=light-dark(#9370DB,#cccccc);size=65;lifelineMirror=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Monitoring" id="O1xT6FS1MFyyzcLnytJf-6">
          <mxCell parent="qIzsALQXbfJu2t_9ep7G-0" style="html=1;shape=umlLifeline;perimeter=lifelinePerimeter;whiteSpace=wrap;container=1;dropTarget=0;collapsible=0;recursiveResize=0;outlineConnect=0;portConstraint=eastwest;newEdgeStyle={&quot;edgeStyle&quot;:&quot;sequenceEdgeStyle&quot;,&quot;libavoidRouting&quot;:0,&quot;curved&quot;:0,&quot;rounded&quot;:0};lifelineDashed=0;strokeWidth=2;rounded=1;absoluteArcSize=1;arcSize=6;lifelineColor=light-dark(#9370DB,#cccccc);size=65;lifelineMirror=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="608" width="150" x="1150" y="10" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="POST /api/v1/device/sales (ključ avtomata)" mermaidId="e:S-&gt;G#0" mermaidBaseStyle="endArrow=block;endSize=9;verticalAlign=bottom;spacingBottom=2;edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=0;rounded=0;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" mermaidBaseValue="POST /api/v1/device/sales (ključ avtomata)" id="O1xT6FS1MFyyzcLnytJf-7">
          <mxCell edge="1" parent="qIzsALQXbfJu2t_9ep7G-0" source="O1xT6FS1MFyyzcLnytJf-2" style="endArrow=block;endSize=9;verticalAlign=bottom;spacingBottom=2;edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=0;rounded=0;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" target="O1xT6FS1MFyyzcLnytJf-3">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="270" y="121" />
              </Array>
              <mxPoint x="85" y="121" as="sourcePoint" />
              <mxPoint x="454" y="121" as="targetPoint" />
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="POST /v1/sales" mermaidId="e:G-&gt;SA#0" mermaidBaseStyle="endArrow=block;endSize=9;verticalAlign=bottom;spacingBottom=2;edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=0;rounded=0;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" mermaidBaseValue="POST /v1/sales" id="O1xT6FS1MFyyzcLnytJf-8">
          <mxCell edge="1" parent="qIzsALQXbfJu2t_9ep7G-0" source="O1xT6FS1MFyyzcLnytJf-3" style="endArrow=block;endSize=9;verticalAlign=bottom;spacingBottom=2;edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=0;rounded=0;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" target="O1xT6FS1MFyyzcLnytJf-4">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="554" y="167" />
              </Array>
              <mxPoint x="454" y="167" as="sourcePoint" />
              <mxPoint x="654" y="167" as="targetPoint" />
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:SA-&gt;SA#0" mermaidBaseStyle="edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=1;endArrow=block;endSize=9;verticalAlign=bottom;align=center;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" mermaidBaseValue="" id="O1xT6FS1MFyyzcLnytJf-9">
          <mxCell edge="1" parent="qIzsALQXbfJu2t_9ep7G-0" source="O1xT6FS1MFyyzcLnytJf-4" style="edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=1;endArrow=block;endSize=9;verticalAlign=bottom;align=center;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" target="O1xT6FS1MFyyzcLnytJf-4">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="689" y="213" />
                <mxPoint x="693" y="225" />
                <mxPoint x="689" y="237" />
              </Array>
              <mxPoint x="654" y="213" as="sourcePoint" />
              <mxPoint x="654" y="237" as="targetPoint" />
            </mxGeometry>
          </mxCell>
        </UserObject>
        <mxCell id="O1xT6FS1MFyyzcLnytJf-10" parent="qIzsALQXbfJu2t_9ep7G-0" style="text;html=1;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;fontColor=light-dark(#333333,#cccccc);" value="preveri izdelek in predal, shrani prodajo + outbox" vertex="1">
          <mxGeometry height="18" width="200" x="554" y="187" as="geometry" />
        </mxCell>
        <UserObject label="201 (ponovitev 200, drugačno telo 409)" mermaidId="e:SA-&gt;S#0" mermaidBaseStyle="dashed=1;fixDash=1;dashPattern=3 3;endArrow=block;endSize=9;verticalAlign=bottom;spacingBottom=2;edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=0;rounded=0;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" mermaidBaseValue="201 (ponovitev 200, drugačno telo 409)" id="O1xT6FS1MFyyzcLnytJf-11">
          <mxCell edge="1" parent="qIzsALQXbfJu2t_9ep7G-0" source="O1xT6FS1MFyyzcLnytJf-4" style="dashed=1;fixDash=1;dashPattern=3 3;endArrow=block;endSize=9;verticalAlign=bottom;spacingBottom=2;edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=0;rounded=0;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" target="O1xT6FS1MFyyzcLnytJf-2">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="370" y="289" />
              </Array>
              <mxPoint x="654" y="289" as="sourcePoint" />
              <mxPoint x="85" y="289" as="targetPoint" />
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="delavec: decrease po saleId" mermaidId="e:SA-&gt;I#0" mermaidBaseStyle="endArrow=block;endSize=9;verticalAlign=bottom;spacingBottom=2;edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=0;rounded=0;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" mermaidBaseValue="delavec: decrease po saleId" id="O1xT6FS1MFyyzcLnytJf-12">
          <mxCell edge="1" parent="qIzsALQXbfJu2t_9ep7G-0" source="O1xT6FS1MFyyzcLnytJf-4" style="endArrow=block;endSize=9;verticalAlign=bottom;spacingBottom=2;edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=0;rounded=0;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" target="O1xT6FS1MFyyzcLnytJf-5">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="779" y="335" />
              </Array>
              <mxPoint x="654" y="335" as="sourcePoint" />
              <mxPoint x="903" y="335" as="targetPoint" />
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:I-&gt;I#0" mermaidBaseStyle="edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=1;endArrow=block;endSize=9;verticalAlign=bottom;align=center;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" mermaidBaseValue="" id="O1xT6FS1MFyyzcLnytJf-13">
          <mxCell edge="1" parent="qIzsALQXbfJu2t_9ep7G-0" source="O1xT6FS1MFyyzcLnytJf-5" style="edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=1;endArrow=block;endSize=9;verticalAlign=bottom;align=center;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" target="O1xT6FS1MFyyzcLnytJf-5">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="938" y="381" />
                <mxPoint x="942" y="393" />
                <mxPoint x="938" y="405" />
              </Array>
              <mxPoint x="903" y="381" as="sourcePoint" />
              <mxPoint x="903" y="405" as="targetPoint" />
            </mxGeometry>
          </mxCell>
        </UserObject>
        <mxCell id="O1xT6FS1MFyyzcLnytJf-14" parent="qIzsALQXbfJu2t_9ep7G-0" style="text;html=1;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;fontColor=light-dark(#333333,#cccccc);" value="premik enkrat + outbox nizke zaloge" vertex="1">
          <mxGeometry height="18" width="200" x="803" y="355" as="geometry" />
        </mxCell>
        <UserObject label="delavec: POST /v1/alerts LOW_STOCK" mermaidId="e:I-&gt;M#0" mermaidBaseStyle="endArrow=block;endSize=9;verticalAlign=bottom;spacingBottom=2;edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=0;rounded=0;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" mermaidBaseValue="delavec: POST /v1/alerts LOW_STOCK" id="O1xT6FS1MFyyzcLnytJf-15">
          <mxCell edge="1" parent="qIzsALQXbfJu2t_9ep7G-0" source="O1xT6FS1MFyyzcLnytJf-5" style="endArrow=block;endSize=9;verticalAlign=bottom;spacingBottom=2;edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=0;rounded=0;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" target="O1xT6FS1MFyyzcLnytJf-6">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1064" y="457" />
              </Array>
              <mxPoint x="903" y="457" as="sourcePoint" />
              <mxPoint x="1225" y="457" as="targetPoint" />
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:M-&gt;M#0" mermaidBaseStyle="edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=1;endArrow=block;endSize=9;verticalAlign=bottom;align=center;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" mermaidBaseValue="" id="O1xT6FS1MFyyzcLnytJf-16">
          <mxCell edge="1" parent="qIzsALQXbfJu2t_9ep7G-0" source="O1xT6FS1MFyyzcLnytJf-6" style="edgeStyle=sequenceEdgeStyle;libavoidRouting=0;curved=1;endArrow=block;endSize=9;verticalAlign=bottom;align=center;fontSize=16;labelBackgroundColor=none;strokeWidth=1.5;strokeColor=light-dark(#333333,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;" target="O1xT6FS1MFyyzcLnytJf-6">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1260" y="503" />
                <mxPoint x="1264" y="515" />
                <mxPoint x="1260" y="527" />
              </Array>
              <mxPoint x="1225" y="503" as="sourcePoint" />
              <mxPoint x="1225" y="527" as="targetPoint" />
            </mxGeometry>
          </mxCell>
        </UserObject>
        <mxCell id="O1xT6FS1MFyyzcLnytJf-17" parent="qIzsALQXbfJu2t_9ep7G-0" style="text;html=1;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;fontColor=light-dark(#333333,#cccccc);" value="alarm + e-poštni outbox" vertex="1">
          <mxGeometry height="18" width="200" x="1125" y="477" as="geometry" />
        </mxCell>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>

```

- Simulator dogodek hrani in ga pošilja, dokler ne dobi 2xx.
- Neznan ključ: 401. Ključ druge naprave: 403 `MACHINE_KEY_MISMATCH`.
- Product ali Inventory nedosegljiv ob sprejemu: 503, nič se ne shrani, simulator ponovi.
- Zmanjšanje se ponavlja z naraščajočim zamikom, dokler Inventory ne odgovori.
- Premalo zaloge, drug izdelek ali pozna prodaja: zaloga se ne zmanjša, ni negativne količine, premik je `NEEDS_RECONCILIATION`; prodaja se nikoli ne izbriše.

### 5.2 Meritev, alarm, e-pošta

1. Simulator vsako minuto pošlje `POST /api/v1/device/measurements`.
2. Monitoring shrani meritev (isti `measurementId` z istim telesom: 200, drugačno telo: 409) in oceni pravila (6.2).
3. Alarm se odpre ali razreši; ob odprtju se zapiše e-poštni outbox.
4. Delavec pošlje e-pošto; ob napaki ponovi z naraščajočim zamikom. Napaka SMTP alarma ne spremeni.
5. Periodično opravilo za `ACTIVE` avtomate odpre `OFFLINE` (brez meritve več kot 5 min) in `DOOR_OPEN` (vrata odprta več kot 120 s).

### 5.3 Prijava operaterja

Registracije ni. Prvega operaterja ob prvem zagonu ustvari gateway iz `BOOTSTRAP_OPERATOR_EMAIL` in `BOOTSTRAP_OPERATOR_PASSWORD`, vendar samo, če še ni nobenega računa. Nadaljnje račune ustvari operater ([03 §9](03-api-pogodbe.md#9-gateway-in-frontend-api-darko-frontend)).

1. Web UI pošlje e-naslov in geslo na `POST /api/v1/auth/login`.
2. Gateway preveri geslo in stanje računa ter vrne podpisan žeton (JWT, velja 8 ur). Neznan e-naslov, napačno geslo in deaktiviran račun dobijo enak odgovor 401.
3. UI žeton hrani za čas zavihka in ga pošilja v glavi `Authorization`; ob 401 prikaže prijavo.
4. Gateway ob vsaki operaterski zahtevi preveri žeton in ali je račun še aktiven, nato storitvi posreduje `X-Operator-Id`.
5. Odjava, sprememba lastnega gesla, administrativna ponastavitev gesla drugega operaterja (`PUT /api/v1/operators/{id}/password`) in deaktivacija računa takoj razveljavijo vse žetone tega računa. Operater ne more deaktivirati sebe; vsaj en aktiven račun vedno ostane.

Frontend API kliče storitve vzporedno; vsak odsek pogleda ima `status` (`OK`, `STALE`, `UNAVAILABLE`) in `asOf`. Prazen seznam nikoli ne nadomesti nedosegljive storitve.

### 5.4 Javni zemljevid

`GET /api/v1/public/machines` in `.../{id}/availability` delujeta brez prijave. Umaknjeni avtomati so skriti, avtomat v servisu je prikazan kot nerazpoložljiv, potekla zaloga in prazni predali so izpuščeni (datum: B4). Javni odgovori ne vsebujejo prodaj, alarmov, količin, kod serij ali ključev.

## 6. Stanja in pravila

### 6.1 Avtomat

Nov avtomat je `ACTIVE` (vsaj en predal, prvi ključ se izda enkrat). `ACTIVE` in `SERVICE` se prosto menjata. Iz obeh je prehod v `RETIRED` (končno, logičen umik), vendar samo, ko v Inventory ni zaloge (409 `MACHINE_HAS_STOCK`); umik prekliče vse ključe. Nadaljnje spremembe: 409 `MACHINE_RETIRED`. Predali se po ustvarjanju ne spreminjajo. Avtomat ima največ 3 aktivne ključe; preklican ključ takoj preneha veljati.

### 6.2 Alarmi

| Tip | Odpre se | Razreši se |
|---|---|---|
| `TEMPERATURE_HIGH` | 2 zaporedni meritvi nad 8 °C | meritev največ 7 °C |
| `DOOR_OPEN` | vrata odprta več kot 120 s | zaprta vrata |
| `OFFLINE` | brez meritve več kot 300 s | nova meritev |
| `LOW_STOCK` | poročilo Inventory (en alarm na avtomat s seznamom predalov) | poročilo s praznim seznamom |

Pragovi so nastavljivi po avtomatu; izbris pragov vrne privzete. Za avtomat in tip je največ en `ACTIVE` alarm. Potrditev operaterja alarma ne razreši. Operater lahko ročno razreši `TEMPERATURE_HIGH`, `DOOR_OPEN` in `OFFLINE`; če pogoj še traja, se odpre nov alarm. `LOW_STOCK` razreši samo poročilo Inventory.

### 6.3 Uskladitev prodaje in dnevni zaključek

`PENDING` preide v `APPLIED` (Inventory je izvedel premik) ali `NEEDS_RECONCILIATION` (neskladje). Ob napaki omrežja ostane `PENDING` in se ponavlja. Operater neskladje zapre: `NEEDS_RECONCILIATION` → `RESOLVED` (končno) z razlogom in opombo; Sales zaloge ne spreminja.

Dnevni zaključek je posnetek prodaj enega dne (UTC) za en avtomat ali vse. `DRAFT` se lahko preračuna ali izbriše; `CONFIRMED` je končen in zahteva, da v obsegu ni odprtih uskladitev.

### 6.4 Predal in polnitev

- En predal ima en izdelek in eno serijo.
- Menjava izdelka pri količini večji od 0: 409 `SLOT_NOT_EMPTY`. Praznjenje predala in odpis potekle zaloge zapišeta premik `REMOVAL`.
- Polnitev z istim `refillId` ne podvoji količine; druga serija v nepraznem predalu: 409 `SLOT_HAS_OTHER_BATCH`.
- `decrease` kliče samo Sales, enkrat po `saleId`.
- Sprememba praga, praznjenje in odpis sprožijo novo poročilo nizke zaloge.

### 6.5 Izdelek in kategorija

Izdelek se arhivira samo brez odprte uskladitve prodaje (409 `PRODUCT_HAS_OPEN_RECONCILIATION`). Kategorija se izbriše samo, če je ne uporablja noben izdelek, tudi arhiviran (409 `CATEGORY_IN_USE`).

### 6.6 Operaterski račun

`ACTIVE` → `DEACTIVATED` (končno; e-naslov ostane zaseden). Deaktiviran račun se ne more prijaviti, njegovi žetoni so preklicani.

Odjava, sprememba gesla, ponastavitev gesla in deaktivacija povečajo `token_version` računa; stari žetoni z drugačno verzijo dobijo 401. Ponastavitev gesla drugemu operaterju ne spremeni stanja računa in je dovoljena samo za `ACTIVE` račun (`PUT /api/v1/operators/{id}/password`); na lastnem računu se ne izvede (uporabi `PUT /api/v1/auth/password`).

## 7. Varnost

| Poverilnica | Izda | Preveri |
|---|---|---|
| Geslo operaterja | operater; prvi operater iz okolja (`BOOTSTRAP_OPERATOR_PASSWORD`) | gateway; shranjen je samo zgoščen zapis, najmanj 12 znakov |
| JWT operaterja | gateway ob prijavi, podpisan z `JWT_SECRET` | gateway ob vsaki zahtevi: podpis, rok, aktiven račun |
| Ključ avtomata (`X-Machine-Key`) | Machine ob ustvarjanju avtomata ali novem ključu, prikazan enkrat | gateway prek Machine; Sales in Monitoring preverita še `machineId` |
| Notranji žeton (`X-Service-Token`) | po en za vsako komponento, ki kliče druge (šest), v okolju | klicana storitev; dovoljeni klicatelji po poti so v [03](03-api-pogodbe.md) |

Gateway ločuje štiri razrede poti: prijava (5 neuspešnih poskusov v 15 minutah, nato 429), javne (`/api/v1/public/*`), naprave (`/api/v1/device/*`, ključ avtomata) in operaterske (`/api/v1/operator/*`, `/api/v1/auth/*`, `/api/v1/operators` ter prehodne poti, JWT). Glave `X-Operator-Id`, `X-Machine-Id` in `X-Service-Token` odjemalca gateway zavrže. Prehodne poti ne izpostavijo `decrease`, `POST /alerts`, notranjih poti ter zapisov meritev in prodaj. Vsaka komponenta pozna samo geslo svoje baze. Zunaj velja HTTPS, lokalno HTTP. Skrivnosti so samo v okolju, ne v kodi in ne v dnevnikih.

## 8. Skupne konvencije

| Tema | Pravilo |
|---|---|
| Napake | `{code, message, traceId}`; kode v [03](03-api-pogodbe.md#2-napake) |
| Metode HTTP | GET bere, POST ustvari ali sproži dejanje, PUT zamenja ali nastavi stanje, DELETE odstrani (logično, razen kjer [03](03-api-pogodbe.md) pove drugače) |
| Idempotenca | prodaja po `saleId`, meritev po `measurementId`, polnitev po `refillId`, `LOW_STOCK` po `version` |
| Sled | `X-Trace-Id` nastane na gatewayu in potuje med storitvami ter v dnevnike |
| Klici | vsak klic ima časovno omejitev; ponovijo se samo GET in idempotentni klici |
| Baza | vsaka komponenta ima svojo; bazen povezav ima največ 10 povezav; tabele ustvarijo samo migracije komponente ob zagonu |
| Konfiguracija | samo okolje: `.env` (vzorec `.env.example`); Compose vsakemu zabojniku poda samo njegove spremenljivke |
| OpenAPI | komponenta interno streže `/openapi.json`; enak mora biti potrjeni `backend/<komponenta>/openapi.yaml` (preverja CI) |
| Zdravje | `/health/live` (proces teče) in `/health/ready` (dosegljiva lastna baza) v vseh komponentah, tudi v gatewayu; nista v OpenAPI |

## 9. Struktura komponent

| | Python | Java | Node | Frontend |
|---|---|---|---|---|
| Komponente | product-, inventory-service, simulator, skripte | machine-, monitoring-, sales-service | gateway | frontend |
| Ogrodje | Python 3.12, FastAPI | Java 21, Spring Boot 4.1, Maven | Node.js 24, Express, TypeScript | React, TypeScript, Vite |
| Baza in bazen | SQLAlchemy 2 + psycopg 3 (5 + 5) | Spring Data JPA, HikariCP (5) | `pg` (5) | — |
| Migracije | Alembic | Flyway | node-pg-migrate | — |
| OpenAPI | generira FastAPI | springdoc (`/openapi.json`) | najprej `openapi.yaml`, zahteve preverja express-openapi-validator | tipi iz `backend/gateway/openapi.yaml` |
| Odjemalec HTTP | httpx | RestClient | fetch | fetch |
| Testi | pytest, Testcontainers | JUnit 5, Spring Boot Test, Testcontainers | Vitest, supertest, Testcontainers | Vitest, Testing Library |
| Lint | Ruff | Spotless | ESLint, Prettier | ESLint |
| Slika | python:3.12-alpine | eclipse-temurin:21-jre-alpine | node:24-alpine | nginx-unprivileged:alpine |
| Predloga `core` | `app/core/` (product-service) | paket `core` (machine-service) | `src/core/` | — |

Vse slike se gradijo večstopenjsko, tečejo kot uporabnik brez korenskih pravic in vsebujejo samo produkcijske odvisnosti.

```
backend/<storitev>/        Python (product-, inventory-service)
  app/       api/v1, domain, repository, clients, workers, core
  alembic/
  tests/     unit, integration
  Dockerfile  pyproject.toml  openapi.yaml

backend/<storitev>/        Java (machine-, monitoring-, sales-service)
  src/main/java/…/     api, domain, repository, client, worker, core
  src/main/resources/  application.yml, db/migration/
  src/test/java/…/     unit, integration
  Dockerfile  pom.xml  mvnw  openapi.yaml

backend/gateway/           Node
  src/       auth, operators, routes/{public,device,operator}, proxy, views, clients, core
  migrations/
  tests/
  Dockerfile  package.json  tsconfig.json  openapi.yaml
```

Odvisnosti tečejo v eno smer: api → domain → repository in clients. Skupne kode med komponentami ni. `core` (napake, sled, notranji žeton, zdravje, nastavitve) je predloga, ki jo vsaka tehnologija kopira: Python iz `backend/product-service/app/core/`, Java iz `backend/machine-service/` (paket `core`), Node je `backend/gateway/src/core/`. Delavci (outbox, periodična opravila) tečejo v procesu storitve.

- Gateway (`backend/gateway/`): `auth/` (prijava, žeton, varovalo poti), `operators/` (računi), `routes/`, `proxy/`, `views/` (Frontend API), `clients/`, `core/`.
- Frontend (`frontend/`): `src/` z `api/` (tipi iz `backend/gateway/openapi.yaml`), `auth/`, `features/`, `components/`. Vite pri razvoju bere korenski `.env` (`envDir: '..'`); v zabojniku je `VITE_API_URL` podan ob gradnji.
- Simulator (`backend/simulator/`): neposlane dogodke ponavlja z istim ID-jem do odgovora 2xx; ključi so v `backend/simulator/machines.local.json` (ustvari ga seed, ni v gitu).

Testi baze tečejo na Postgresu v Testcontainers. Zagon celote: §10.

## 10. Zagon v Dockerju

| Zabojnik | Slika | Vrata na računalniku | Počaka na |
|---|---|---|---|
| `machine-service`, `monitoring-service`, `sales-service`, `product-service`, `inventory-service` | `backend/<storitev>/Dockerfile` | — | svojo bazo |
| `gateway` | `backend/gateway/Dockerfile` | 8080 | `gateway-db` |
| `machine-db`, `monitoring-db`, `product-db`, `inventory-db`, `sales-db`, `gateway-db` | `postgres:17-alpine` | — | — |
| `mailpit` | Mailpit | 8025 (spletni vmesnik) | — |
| `frontend` | `frontend/Dockerfile` | 5173 | — |
| `simulator` (profil `sim`) | `backend/simulator/Dockerfile` | — | `gateway` |

- Vsak zabojnik ima preverjanje zdravja: baze `pg_isready`, komponente `/health/ready`, ostali lastno preverjanje.
- Komponenta počaka na zdravo lastno bazo, zažene migracije in šele nato sprejema zahteve. Med storitvami ni odvisnosti zagona; storitev, ki še ni pripravljena, se obravnava kot nedosegljiva (§3).
- Gateway počaka samo na svojo bazo. Dokler storitev ni pripravljena, prehodne poti vračajo 503, odseki pogledov pa `UNAVAILABLE`.
- Zabojnik se po padcu znova zažene. Vsak ima omejen pomnilnik: Python 256 MB, Java 512 MB, Node 256 MB, Postgres 256 MB, ostali 64–128 MB.
- Komponente poslušajo na vratih 8000; ime zabojnika je ime v omrežju Compose (npr. `http://sales-service:8000`).
- Podatki baz so v imenovanih nosilcih; `docker compose down -v` jih izbriše.

| Spremenljivka v `.env` | Prejme | Namen |
|---|---|---|
| `MACHINE_DB_PASSWORD`, `MONITORING_DB_PASSWORD`, `PRODUCT_DB_PASSWORD`, `INVENTORY_DB_PASSWORD`, `SALES_DB_PASSWORD`, `GATEWAY_DB_PASSWORD` | baza in njena komponenta | geslo baze |
| `JWT_SECRET` | gateway | podpis žetonov, vsaj 32 znakov |
| `JWT_TTL_MINUTES` | gateway | veljavnost žetona (privzeto 480) |
| `BOOTSTRAP_OPERATOR_EMAIL`, `BOOTSTRAP_OPERATOR_PASSWORD` | gateway, `backend/seed.py` | prvi operater |
| `CORS_ORIGINS` | gateway | izvor Web UI (`http://localhost:5173`) |
| `GATEWAY_SERVICE_TOKEN`, `MACHINE_SERVICE_TOKEN`, `MONITORING_SERVICE_TOKEN`, `PRODUCT_SERVICE_TOKEN`, `INVENTORY_SERVICE_TOKEN`, `SALES_SERVICE_TOKEN` | klicatelj in klicane storitve | notranji žetoni |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM`, `ALERT_EMAIL_TO` | monitoring | e-pošta alarmov |
| `VITE_API_URL` | frontend ob gradnji | naslov gatewaya (`http://localhost:8080`) |

Compose iz teh vrednosti sestavi povezavo do baze: Python (product-, inventory-service) `DATABASE_URL=postgresql+psycopg://product:<geslo>@product-db:5432/product`, Node `DATABASE_URL=postgres://gateway:<geslo>@gateway-db:5432/gateway`, Java (machine-, monitoring-, sales-service) `SPRING_DATASOURCE_URL=jdbc:postgresql://machine-db:5432/machine` s `SPRING_DATASOURCE_USERNAME` in `SPRING_DATASOURCE_PASSWORD`. Naslovi storitev (`MACHINE_URL`, `MONITORING_URL`, `PRODUCT_URL`, `INVENTORY_URL`, `SALES_URL`, za simulator `GATEWAY_URL`) so v `docker-compose.yml`, ne v `.env`.

**Preverjanje**

- `backend/smoke_test.py` gre samo prek gatewaya čez glavni tok: prijava, avtomat, kategorija in izdelek, polnitev, prodaja s ključem avtomata, zmanjšana zaloga, alarm `LOW_STOCK` z e-pošto v Mailpit, zavrnjen umik avtomata z zalogo, zavrnjeno arhiviranje izdelka z odprto uskladitvijo, uskladitev in odjava.
- `backend/export_openapi.py` zapiše OpenAPI vsake tekoče komponente v njen `openapi.yaml`; z `--check` samo primerja.
- CI opravilo `smoke` zažene Compose iz praznih baz in oba skripta.

Ukazi: [README](../README.md#lokalni-zagon-načrtovano).

## 11. Zahteve predmeta in vzorci

| Zahteva predmeta | Kje je izpolnjena |
|---|---|
| Vsaj dve storitvi na člana (Frontend API vsaj eno) | §1, [02](02-razdelitev-dela.md) |
| Vsaka storitev ima svojo bazo | šest zabojnikov `postgres:17-alpine` (§1, §10) |
| Lasten Dockerfile, zagon v Dockerju | `backend/<komponenta>/Dockerfile`, `frontend/Dockerfile`, `docker-compose.yml` (§10) |
| OpenAPI, skladen z implementacijo | `/openapi.json` je enak `openapi.yaml`, preverja CI (§8, §10) |
| Dobre prakse, minimalni viri | bazen povezav, metode HTTP (§8), alpine, večstopenjska gradnja, uporabnik brez korenskih pravic, omejitve virov (§9, §10) |
| Vsaj dvakrat GET, POST, PUT, DELETE na storitev | [03 §11](03-api-pogodbe.md#11-pokritost-metod-pogoj-predmeta) |
| Vsaka storitev kliče storitev drugega člana | §3 |
| Vsaj tri ogrodja | Spring Boot, FastAPI, Express (§9) |
| Lokalni zagon z delujočimi klici med storitvami | `backend/smoke_test.py`, CI opravilo `smoke` (§10) |

| Vzorec ([microservices.io](https://microservices.io/patterns/)) | Kje |
|---|---|
| API Gateway | gateway: edina vstopna točka, prijava, usmerjanje |
| Backend for Frontend, API Composition | Frontend API: pogledi za Web UI iz več storitev |
| Database per Service | šest baz, brez skupnih tabel |
| Transactional Outbox, Polling Publisher | Sales (zmanjšanje), Inventory (`LOW_STOCK`), Monitoring (e-pošta); delavec bere outbox |
| Idempotent Consumer | prodaja, meritev, polnitev, zmanjšanje po ID-ju, `LOW_STOCK` po `version` |
| Access Token | JWT operaterja; gateway posreduje `X-Operator-Id` |
| Health Check API | `/health/live`, `/health/ready`, preverjanje zdravja v Compose |
| Service Registry, Server-side Discovery | ponuja ju Docker: imena zabojnikov v DNS omrežja Compose |
| Distributed Tracing | `X-Trace-Id` v vseh klicih in dnevnikih (brez zbiralnika sledi) |
| Externalized Configuration | spremenljivke okolja (`.env`) |
| Microservice Chassis | predloga `core` po tehnologiji (kopija, ne skupna knjižnica) |
| Service Instance per Container | en zabojnik na komponento |
| Remote Procedure Invocation | REST s časovnimi omejitvami |

Posrednika sporočil in Circuit Breakerja ne uporabljamo.
