# Arhitektura (načrtovano)

Kako sistem deluje. Izhodišče: [skupina_10.pdf](skupina_10.pdf). Kdo kaj dela: [02-razdelitev-dela.md](02-razdelitev-dela.md). Končne točke: [03-api-pogodbe.md](03-api-pogodbe.md). Oznake B1–B6 so odprte odločitve ([02](02-razdelitev-dela.md#odprte-odločitve)).

## 1. Komponente

```drawio
<mxfile>
  <diagram id="fspCAeVNdPlCLKPe7liJ" name="Page-1">
    <mxGraphModel dx="932" dy="861" grid="1" gridSize="10" guides="1" tooltips="0" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="827" pageHeight="1169" math="0" shadow="0">
      <root>
        <mxCell id="Y9MFopimnAgydAU9Kui9-0" />
        <mxCell id="Y9MFopimnAgydAU9Kui9-1" parent="Y9MFopimnAgydAU9Kui9-0" />
        <UserObject label="" mermaidData="{&#xa;  &quot;data&quot;: &quot;flowchart LR\n  OP[\&quot;Operater (brskalnik)\&quot;] --&gt; UI[Web UI]\n  KU[\&quot;Kupec (javna stran)\&quot;] --&gt; UI\n  UI -. prijava .-&gt; AUTH[Supabase Auth]\n  UI --&gt; GW[\&quot;Gateway + Frontend API\&quot;]\n  SIM[Simulator] --&gt; GW\n  GW -. JWKS .-&gt; AUTH\n  GW --&gt; MA[Machine]\n  GW --&gt; MO[Monitoring]\n  GW --&gt; PR[Product]\n  GW --&gt; IN[Inventory]\n  GW --&gt; SA[Sales]\n  SA --&gt; PR\n  SA --&gt; IN\n  IN --&gt; MA\n  IN --&gt; PR\n  IN --&gt; MO\n  MO --&gt; MA\n  MO -. SMTP .-&gt; MAIL[Mailpit]&quot;,&#xa;  &quot;config&quot;: null&#xa;}" id="8JC47QpPdXyxZVrQ8w-1-0">
          <mxCell connectable="0" parent="Y9MFopimnAgydAU9Kui9-1" style="group;transparentBounds=1;editIcon=1;lockedGroup=0;groupPadding=10;" vertex="1">
            <mxGeometry as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Operater (brskalnik)" mermaidId="n:OP" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Operater (brskalnik)" id="Y9MFopimnAgydAU9Kui9-2">
          <mxCell parent="8JC47QpPdXyxZVrQ8w-1-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="205" x="10" y="10" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Web UI" mermaidId="n:UI" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Web UI" id="Y9MFopimnAgydAU9Kui9-3">
          <mxCell parent="8JC47QpPdXyxZVrQ8w-1-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="110" x="275" y="25" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Kupec (javna stran)" mermaidId="n:KU" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Kupec (javna stran)" id="Y9MFopimnAgydAU9Kui9-4">
          <mxCell parent="8JC47QpPdXyxZVrQ8w-1-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="200" x="13" y="114" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Supabase Auth" mermaidId="n:AUTH" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Supabase Auth" id="Y9MFopimnAgydAU9Kui9-5">
          <mxCell parent="8JC47QpPdXyxZVrQ8w-1-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="163" x="764" y="10" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Gateway + Frontend API" mermaidId="n:GW" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Gateway + Frontend API" id="Y9MFopimnAgydAU9Kui9-6">
          <mxCell parent="8JC47QpPdXyxZVrQ8w-1-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="232" x="444" y="139" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Simulator" mermaidId="n:SIM" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Simulator" id="Y9MFopimnAgydAU9Kui9-7">
          <mxCell parent="8JC47QpPdXyxZVrQ8w-1-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="129" x="265" y="149" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Machine" mermaidId="n:MA" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Machine" id="Y9MFopimnAgydAU9Kui9-8">
          <mxCell parent="8JC47QpPdXyxZVrQ8w-1-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="118" x="1378" y="246" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Monitoring" mermaidId="n:MO" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Monitoring" id="Y9MFopimnAgydAU9Kui9-9">
          <mxCell parent="8JC47QpPdXyxZVrQ8w-1-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="136" x="1155" y="343" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Product" mermaidId="n:PR" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Product" id="Y9MFopimnAgydAU9Kui9-10">
          <mxCell parent="8JC47QpPdXyxZVrQ8w-1-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="115" x="1166" y="164" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Inventory" mermaidId="n:IN" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Inventory" id="Y9MFopimnAgydAU9Kui9-11">
          <mxCell parent="8JC47QpPdXyxZVrQ8w-1-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="128" x="977" y="250" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Sales" mermaidId="n:SA" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Sales" id="Y9MFopimnAgydAU9Kui9-12">
          <mxCell parent="8JC47QpPdXyxZVrQ8w-1-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="96" x="798" y="178" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="Mailpit" mermaidId="n:MAIL" mermaidBaseStyle="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" mermaidBaseValue="Mailpit" id="Y9MFopimnAgydAU9Kui9-13">
          <mxCell parent="8JC47QpPdXyxZVrQ8w-1-0" style="html=1;whiteSpace=wrap;strokeWidth=1;fillColor=light-dark(#ECECFF,#1f2020);strokeColor=light-dark(#9370DB,#cccccc);fontColor=light-dark(#333333,#cccccc);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontSize=16;" vertex="1">
            <mxGeometry height="54" width="109" x="1383" y="350" as="geometry" />
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:OP-&gt;UI#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0;entryY=0.33;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-14">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-2" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0;entryY=0.33;" target="Y9MFopimnAgydAU9Kui9-3">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="240" y="37" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:KU-&gt;UI#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0.25;entryY=1;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-15">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-4" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0.25;entryY=1;" target="Y9MFopimnAgydAU9Kui9-3">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="240" y="141" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="prijava" mermaidId="e:UI-&gt;AUTH#0" mermaidBaseStyle="curved=1;dashed=1;dashPattern=2 3;fixDash=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);html=1;fontSize=16;labelBackgroundColor=light-dark(#E8E8E88D,#2a2a2a8D);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.22;entryX=0;entryY=0.39;" mermaidBaseValue="prijava" id="Y9MFopimnAgydAU9Kui9-16">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-3" style="curved=1;dashed=1;dashPattern=2 3;fixDash=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);html=1;fontSize=16;labelBackgroundColor=light-dark(#E8E8E88D,#2a2a2a8D);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.22;entryX=0;entryY=0.39;" target="Y9MFopimnAgydAU9Kui9-5">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="419" y="27" />
                <mxPoint x="560" y="27" />
                <mxPoint x="720" y="27" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:UI-&gt;GW#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.76;exitY=1;entryX=0;entryY=0.02;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-17">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-3" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.76;exitY=1;entryX=0;entryY=0.02;" target="Y9MFopimnAgydAU9Kui9-6">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="419" y="135" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:SIM-&gt;GW#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0;entryY=0.65;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-18">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-7" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0;entryY=0.65;" target="Y9MFopimnAgydAU9Kui9-6">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="419" y="176" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="JWKS" mermaidId="e:GW-&gt;AUTH#0" mermaidBaseStyle="curved=1;dashed=1;dashPattern=2 3;fixDash=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);html=1;fontSize=16;labelBackgroundColor=light-dark(#E8E8E88D,#2a2a2a8D);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontColor=light-dark(#333333,#cccccc);exitX=0.67;exitY=0;entryX=0;entryY=0.76;" mermaidBaseValue="JWKS" id="Y9MFopimnAgydAU9Kui9-19">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-6" style="curved=1;dashed=1;dashPattern=2 3;fixDash=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);html=1;fontSize=16;labelBackgroundColor=light-dark(#E8E8E88D,#2a2a2a8D);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontColor=light-dark(#333333,#cccccc);exitX=0.67;exitY=0;entryX=0;entryY=0.76;" target="Y9MFopimnAgydAU9Kui9-5">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="720" y="58" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:GW-&gt;MA#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.8;exitY=0;entryX=0.36;entryY=0;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-20">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-6" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.8;exitY=0;entryX=0.36;entryY=0;" target="Y9MFopimnAgydAU9Kui9-8">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="720" y="104" />
                <mxPoint x="846" y="104" />
                <mxPoint x="952" y="104" />
                <mxPoint x="1041" y="104" />
                <mxPoint x="1130" y="104" />
                <mxPoint x="1223" y="104" />
                <mxPoint x="1335" y="104" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:GW-&gt;MO#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.6;exitY=1;entryX=0;entryY=0.26;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-21">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-6" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.6;exitY=1;entryX=0;entryY=0.26;" target="Y9MFopimnAgydAU9Kui9-9">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="720" y="352" />
                <mxPoint x="846" y="352" />
                <mxPoint x="952" y="352" />
                <mxPoint x="1041" y="352" />
                <mxPoint x="1130" y="352" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:GW-&gt;PR#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.19;entryX=0.03;entryY=0;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-22">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-6" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.19;entryX=0.03;entryY=0;" target="Y9MFopimnAgydAU9Kui9-10">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="720" y="143" />
                <mxPoint x="846" y="143" />
                <mxPoint x="952" y="143" />
                <mxPoint x="1041" y="143" />
                <mxPoint x="1130" y="143" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:GW-&gt;IN#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.69;exitY=1;entryX=0;entryY=0.37;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-23">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-6" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.69;exitY=1;entryX=0;entryY=0.37;" target="Y9MFopimnAgydAU9Kui9-11">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="720" y="267" />
                <mxPoint x="846" y="267" />
                <mxPoint x="952" y="267" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:GW-&gt;SA#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.97;exitY=1;entryX=0;entryY=0.5;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-24">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-6" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.97;exitY=1;entryX=0;entryY=0.5;" target="Y9MFopimnAgydAU9Kui9-12">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="720" y="205" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:SA-&gt;PR#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0;entryY=0.67;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-25">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-12" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.5;entryX=0;entryY=0.67;" target="Y9MFopimnAgydAU9Kui9-10">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="952" y="205" />
                <mxPoint x="1041" y="205" />
                <mxPoint x="1130" y="205" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:SA-&gt;IN#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.81;exitY=1;entryX=0;entryY=0.8;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-26">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-12" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.81;exitY=1;entryX=0;entryY=0.8;" target="Y9MFopimnAgydAU9Kui9-11">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="952" y="300" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:IN-&gt;MA#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.52;entryX=0;entryY=0.56;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-27">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-11" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.52;entryX=0;entryY=0.56;" target="Y9MFopimnAgydAU9Kui9-8">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1130" y="279" />
                <mxPoint x="1223" y="279" />
                <mxPoint x="1335" y="279" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:IN-&gt;PR#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.98;exitY=0;entryX=0.03;entryY=1;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-28">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-11" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.98;exitY=0;entryX=0.03;entryY=1;" target="Y9MFopimnAgydAU9Kui9-10">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1130" y="238" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:IN-&gt;MO#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.69;exitY=1;entryX=0;entryY=0.59;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-29">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-11" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.69;exitY=1;entryX=0;entryY=0.59;" target="Y9MFopimnAgydAU9Kui9-9">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1130" y="377" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="" mermaidId="e:MO-&gt;MA#0" mermaidBaseStyle="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.94;exitY=0;entryX=0;entryY=1;" mermaidBaseValue="" id="Y9MFopimnAgydAU9Kui9-30">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-9" style="curved=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);exitX=0.94;exitY=0;entryX=0;entryY=1;" target="Y9MFopimnAgydAU9Kui9-8">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1335" y="320" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
        <UserObject label="SMTP" mermaidId="e:MO-&gt;MAIL#0" mermaidBaseStyle="curved=1;dashed=1;dashPattern=2 3;fixDash=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);html=1;fontSize=16;labelBackgroundColor=light-dark(#E8E8E88D,#2a2a2a8D);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.57;entryX=0;entryY=0.5;" mermaidBaseValue="SMTP" id="Y9MFopimnAgydAU9Kui9-31">
          <mxCell edge="1" parent="8JC47QpPdXyxZVrQ8w-1-0" source="Y9MFopimnAgydAU9Kui9-9" style="curved=1;dashed=1;dashPattern=2 3;fixDash=1;startArrow=none;endArrow=block;endSize=7;strokeColor=light-dark(#333333,#cccccc);html=1;fontSize=16;labelBackgroundColor=light-dark(#E8E8E88D,#2a2a2a8D);fontFamily=Trebuchet MS,Verdana,Arial,sans-serif;fontColor=light-dark(#333333,#cccccc);exitX=1;exitY=0.57;entryX=0;entryY=0.5;" target="Y9MFopimnAgydAU9Kui9-13">
            <mxGeometry relative="1" as="geometry">
              <Array as="points">
                <mxPoint x="1335" y="377" />
              </Array>
            </mxGeometry>
          </mxCell>
        </UserObject>
      </root>
    </mxGraphModel>
  </diagram>
</mxfile>

```

Vsaka od petih storitev ima svojo shemo v Postgresu (Supabase), svojo DB vlogo, migracije (Alembic) in zabojnik. Gateway nima baze.

| Komponenta | Lastnik | Skrbi za |
|---|---|---|
| Machine | Aleš | avtomati, predali, administrativno stanje, ključi avtomatov |
| Monitoring | Aleš | meritve, dosegljivost, alarmi, pragovi, e-pošta |
| Product | Nejc | katalog izdelkov |
| Inventory | Nejc | količina in serija v predalu, polnitve, premiki |
| Sales | Aleš | nespremenljiva zgodovina prodaj, statistika |
| Gateway + Frontend API | Darko | preverjanje identitete, usmerjanje, sestavljeni pogledi |
| Web UI | Darko | javni zemljevid, operaterska plošča |
| Simulator | Aleš (B1) | emulira avtomate: meritve in prodaje |

## 2. Načela

- Podatki druge storitve samo prek njenega REST API-ja; sklici so ID-ji, brez tujih ključev med shemami.
- Zunanji odjemalci (Web UI, simulator) samo prek gatewaya; storitve nimajo javnih vrat.
- Sales sporoča Inventory prek outboxa (trajen zapis v isti transakciji, delavec ga pošlje); brez posrednika sporočil.
- Nedosegljiv Monitoring ali SMTP nikoli ne razveljavi spremembe zaloge ali prodaje.
- Poslovna pravila veljajo na strežniku, ne samo v brskalniku.
- Poti: storitve `/v1/...`, gateway `/api/v1/...`.

## 3. Notranji klici

| Kliče | Klicani | Namen |
|---|---|---|
| Sales | Product, Inventory | ob sprejemu prodaje preveri izdelek in predal |
| Sales (outbox) | Inventory | enkratno zmanjšanje zaloge po `saleId` |
| Inventory | Machine, Product | predal in izdelek obstajata |
| Inventory (outbox) | Monitoring | alarm `LOW_STOCK` |
| Monitoring | Machine | seznam aktivnih avtomatov za preverjanje `OFFLINE` |
| Monitoring (outbox) | SMTP | e-pošta ob alarmu |
| Frontend API | vseh pet storitev | sestavljeni pogledi (samo branje) |
| Gateway | Machine | preverjanje ključa avtomata |

## 4. Lastništvo podatkov in enumi

| Storitev | Podatki | Enumi |
|---|---|---|
| Machine | avtomati, predali (`slotCode`), ključi | status: `ACTIVE`, `SERVICE`, `RETIRED` |
| Monitoring | meritve, alarmi, pragovi, e-poštni outbox | alarm: `ACTIVE`, `RESOLVED`; tip: `TEMPERATURE_HIGH`, `DOOR_OPEN`, `OFFLINE`, `LOW_STOCK` |
| Product | izdelki | `ACTIVE`, `ARCHIVED` |
| Inventory | zaloga predala, serije, premiki, outbox nizke zaloge | premik: `SET`, `REFILL`, `REMOVAL`, `SALE`; rezultat: `APPLIED`, `NEEDS_RECONCILIATION` |
| Sales | prodaje, outbox zmanjšanj | uskladitev: `PENDING`, `APPLIED`, `NEEDS_RECONCILIATION` |

Denar je decimalen, časi so UTC. Podatki prodaje se po sprejemu ne spreminjajo.

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

Web UI se prijavi neposredno v Supabase Auth (registracija izklopljena). Gateway preveri JWT in vlogo `operator` ter posreduje identiteto storitvam. Frontend API kliče storitve vzporedno; vsak odsek pogleda ima `status` (`OK`, `STALE`, `UNAVAILABLE`) in `asOf`. Prazen seznam nikoli ne nadomesti nedosegljive storitve.

### 5.4 Javni zemljevid

`GET /api/v1/public/machines` in `.../{id}/availability` delujeta brez prijave. Umaknjeni avtomati so skriti, avtomat v servisu je prikazan kot nerazpoložljiv, potekla zaloga in prazni predali so izpuščeni (datum: B4). Javni odgovori ne vsebujejo prodaj, alarmov, količin, kod serij ali ključev.

## 6. Stanja in pravila

### 6.1 Avtomat

Nov avtomat je `ACTIVE` (vsaj en predal, ključ se izda enkrat). `ACTIVE` in `SERVICE` se prosto menjata. Iz obeh je prehod v `RETIRED` (končno, logičen umik); nadaljnje spremembe: 409 `MACHINE_RETIRED`. Predali se po ustvarjanju ne spreminjajo.

### 6.2 Alarmi

| Tip | Odpre se | Razreši se |
|---|---|---|
| `TEMPERATURE_HIGH` | 2 zaporedni meritvi nad 8 °C | meritev največ 7 °C |
| `DOOR_OPEN` | vrata odprta več kot 120 s | zaprta vrata |
| `OFFLINE` | brez meritve več kot 300 s | nova meritev |
| `LOW_STOCK` | poročilo Inventory (en alarm na avtomat s seznamom predalov) | poročilo s praznim seznamom |

Pragovi so nastavljivi po avtomatu. Za avtomat in tip je največ en `ACTIVE` alarm. Potrditev operaterja alarma ne razreši.

### 6.3 Uskladitev prodaje

`PENDING` preide v `APPLIED` (Inventory je izvedel premik) ali `NEEDS_RECONCILIATION` (neskladje). Ob napaki omrežja ostane `PENDING` in se ponavlja. Zaprtje ročne uskladitve: B2.

### 6.4 Predal in polnitev

- En predal ima en izdelek in eno serijo.
- Menjava izdelka pri količini večji od 0: 409 `SLOT_NOT_EMPTY`. Količina 0 zapiše premik `REMOVAL`.
- Polnitev z istim `refillId` ne podvoji količine; druga serija v nepraznem predalu: 409 `SLOT_HAS_OTHER_BATCH`.
- `decrease` kliče samo Sales, enkrat po `saleId`.

## 7. Varnost

| Poverilnica | Izda | Preveri |
|---|---|---|
| JWT operaterja | Supabase Auth | gateway |
| Ključ avtomata (`X-Machine-Key`) | Machine ob ustvarjanju avtomata, prikazan enkrat | gateway prek Machine; Sales in Monitoring preverita še `machineId` |
| Notranji žeton (`X-Service-Token`) | po en za vsako storitev, ki kliče druge, v okolju | vsaka storitev; seznam dovoljenih klicateljev po poti |

Gateway ločuje tri razrede poti: javne (`/api/v1/public/*`), naprave (`/api/v1/device/*`, ključ avtomata) in operaterske (`/api/v1/operator/*` ter prehodne poti, JWT). Prehodne poti ne izpostavijo `decrease`, `POST /alerts`, notranjih poti ter zapisov meritev in prodaj. Zunaj HTTPS, lokalno HTTP. Skrivnosti so samo v okolju, ne v kodi in dnevnikih. Podrobnosti po poteh: [03](03-api-pogodbe.md).

## 8. Skupne konvencije

| Tema | Pravilo |
|---|---|
| Napake | `{code, message, traceId}`; kode v [03](03-api-pogodbe.md#2-napake) |
| Idempotenca | prodaja po `saleId`, meritev po `measurementId`, polnitev po `refillId`, `LOW_STOCK` po `version` |
| Sled | `X-Trace-Id` nastane na gatewayu in potuje med storitvami |
| Ponovni poskusi | samo GET in idempotentni klici, s časovnimi omejitvami |
| Konfiguracija | samo okolje (`.env`, vzorec `.env.example`) |
| OpenAPI | vsaka komponenta izvozi svoj `openapi.yaml` (`backend/<storitev>/`, `backend/gateway/`) |
| Zdravje | `/health/live`, `/health/ready`; gateway `/health` |

## 9. Struktura storitve

```
backend/<storitev>/
  app/      api/v1, domain, repository, clients, workers, core
  alembic/
  tests/    unit, integration
  Dockerfile  pyproject.toml  openapi.yaml
```

Odvisnosti tečejo v eno smer: api, domain, repository/clients. Pogodba vsake komponente je njen `openapi.yaml`; skupne kode ni, `core/` je kopija predloge iz `backend/machine-service/app/core/` (M0). Delavci tečejo v procesu storitve.

- Gateway (`backend/gateway/`): `app/` z `auth/`, `routes/device|public|operator/`, `proxy/`, `clients/`, `core/`; razredi poti: javne (429), naprave (`X-Machine-Key`), operaterske (JWT).
- Frontend (`frontend/`): `src/` z `api/` (tipi iz `backend/gateway/openapi.yaml`), `auth/`, `features/`, `components/`; Vite bere korenski `.env` (`envDir: '..'`).
- Simulator (`backend/simulator/`): neposlane dogodke ponavlja z istim ID-jem do odgovora 2xx; ključi v `backend/simulator/machines.local.json` (ustvari seed, ni v gitu).

Razvoj poteka na lokalnem Supabase (prava prijava), testi na navadnem Postgresu s testnim JWKS. Zagon: [README](../README.md#lokalni-zagon-načrtovano).
