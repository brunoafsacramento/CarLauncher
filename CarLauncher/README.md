# Car Launcher

Launcher Android para central multimídia. Funciona sem OBD; com um ELM327 Bluetooth, vira também
computador de bordo e ferramenta de diagnóstico.

## Abas
- **Início**: relógio, destinos de um toque (Casa, Trabalho), assistente de voz, música com capa do álbum,
  carro animado com avisos e a barra de atalhos.
- **Apps**: todos os apps, com busca. Segure um app para fixá-lo ou tirá-lo do Início.
- **Motor**: mostradores, consumo do momento, correção de mistura e bateria.
- **Saúde**: nota de 0 a 100, carro em raio-x apontando a região da falha, erros, histórico e relatório.
- **Viagem**: computador de bordo, diário de viagens, etanol ou gasolina, troca de óleo e bateria na partida.
- **Ajustes**: modo demonstração, cores, alertas, destinos, música e manutenção.

## O carro do app
As imagens em `app/src/main/res/drawable-nodpi/` são recortes das fotos do dono, com a placa coberta.
Em `ui/CarPhoto.kt` ficam os ângulos (`CarViews`) e a posição dos faróis e dos pontos de falha em cada foto.

## Testar sem o carro
Ajustes > Modo demonstração. Um carro simulado anda sozinho, com dois erros de exemplo.

## Estrutura do código
- `car/`: `CarHub.kt` é a central (conexão, viagem, consumo, alertas, histórico); `DemoLink.kt` é o carro simulado.
- `obd/`: comunicação com o ELM327.
- `ui/`: telas e efeitos visuais.
- `apps/`, `media/`: apps instalados, atalhos e música.
- `Prefs.kt`: todas as preferências.

`debug.keystore` é a chave que assina o app. Ela é fixa para as versões novas instalarem por cima da anterior.
