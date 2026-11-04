# Testes sugeridos da versão 2.0 (no POCO X7 Pro)

1. **Digitando lentamente:** abrir "Pesquisar por pessoa", digitar letras e conferir que não aparecem novas fichas nem há consulta pesada. Tocar **Pesquisar**, observar resultados.
2. **Termo repetido em fotos sobrepostas:** importar fotos do mesmo quadro (em vários enquadramentos). Buscar nome e confirmar que os achados são organizados por pessoa/MSC ID, tipo e data, com contador de fontes. **Se a data não existir, a ficha avisa que podem ser dias distintos.**
3. **Treinamento das 10h:** documento com `LEARNING AND DEVELOPMENT TRAINING`, `TIME: 10:00`, `DECK 6 - BUSINESS CENTRE` e presença. Buscar uma pessoa dessa lista. A ficha deve indicar TREINAMENTO, não atribuir 10:00 como entrada de turno. Data `05.Nov.2026.At Sea` deve resultar em `05/11/2026` se for reconhecida na foto.
4. **Treinamento das 16h:** foto com `TIME: 16:00` e local Deck 5 ou Deck 6. Verificar que é treinamento e não manhã/tarde. **Não inferir data de `DATE EMBARK`.**
5. **Escala com observação TRAINING:** foto com turno 07:00–12:00 e 13:00–18:00 e observação OTALIO TRAINING 16:00. O tipo de documento deve ser ESCALA, não TREINAMENTO. Os horários devem aparecer como OCR não confirmado.
6. **Tabela de cadastros:** `MSC ID / NAME / POSITION / CABIN / DATE EMBARK` deve ser CADASTRO, não usar DATE EMBARK como data de escala.
7. **Troca de cabine:** fotos `Crew cabin change` devem aparecer como tipo CABINES na biblioteca, não ser tratadas como turno na busca.
8. **Correção manual:** em `Fotos importadas`, entrar em `OCR / tipo / corrigir / excluir`, escolher a categoria, definir data se faltar, e pesquisar novamente.
9. **Ficha confirmada:** em um resultado, tocar `Corrigir/confirmar` e preencher manhã, tarde, pausas e treinamento apenas com o que foi conferido visualmente. Salvar e pesquisar de novo.
10. **Duplicidade exata:** importar o MESMO arquivo de foto novamente. A v2 deve ignorar a cópia repetida (o teste não remove duplicatas que já existiam na versão anterior).
11. **Backup:** após importar 1–2 fotos de teste, fazer backup ZIP. Verificar que há `database.json` e fotos dentro do ZIP. Restaurar SOMENTE quando tiver certeza de que pode substituir os dados atuais. Não faça esse teste com o único backup de dados importantes.
12. **Barra do Android 16:** conferir que relógio e indicadores de bateria não sobrepõem os títulos do aplicativo.
13. **Compilação:** na aba Actions conferir `Compilar APK debug` e `Publicar APK`, depois testar abertura/importação/pesquisa em um aparelho real.

> Identificações, nomes, cabines, embarques e horários das fotografias reais são informações pessoais. Não usar dados reais no repositório público nem em screenshots compartilhadas sem autorização.
