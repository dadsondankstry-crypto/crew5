# Minha Escala 2.0 — Pesquisa sob demanda e OCR organizado

**Projeto Android nativo Java**, pronto para enviar como ZIP ao ZiptoGit/GitHub.

## O que mudou na v2.0

1. **Pesquisar só quando solicitado:** digite nome, sobrenome ou MSC ID e toque em **🔍 Pesquisar** (ou Buscar no teclado). O aplicativo **não** procura nas fotos enquanto você digita. A pesquisa lê o **texto OCR que já foi salvo** e é executada fora da thread principal; resultados são renderizados em pequenos lotes.
2. **Resultados organizados:** uma ficha mostra tipo de documento, nome, MSC ID quando o OCR encontrar, data do documento (se legível), possível setor, horários OCR com aviso de incerteza, foto(s) de origem e botão de correção. Linhas repetidas do mesmo MSC ID e data são agrupadas. **Se não houver data**, fotos do mesmo ID podem representar dias diferentes: essa incerteza é exibida e os horários não são consolidados como se fossem um turno.
3. **Tipos de documentos separados:** ESCALA, TREINAMENTO, CADASTRO de tripulantes, CABINES (mudanças) e AVISO. A palavra "TRAINING" nas observações de uma escala não faz toda a escala virar treinamento. O tipo pode ser corrigido manualmente.
4. **Horas sem adivinhação:** horário de turno, horário de TAC Break, RESTO SERVICE, treinamento e horários `00:00` não são automaticamente misturados. O OCR mostra candidatos para conferência; a entrada e saída ficam vazias até você confirmar.
5. **Ficha completa editável:** nome, MSC ID, data, turno da manhã, turno da tarde, duas pausas, treinamento, setor e observações. Dados antigos da 1.0 são preservados no formato do banco, quando o Android permite instalar a atualização sobre o app antigo.
6. **Fotos duplicadas:** se importar **o mesmo arquivo de imagem** duas vezes na nova versão, o SHA-256 impede uma segunda cópia. Fotos diferentes do mesmo quadro continuam disponíveis, e a pesquisa pode agrupar resultados quando seguro.
7. **Datas aprimoradas:** reconhece `05/11/26`, `5-NOV-26`, `05.Nov.2026`; não usa coluna `DATE EMBARK` como data de trabalho.
8. **Interfaz Android 16:** aplicação de insets de status bar e barra de navegação (importante no POCO X7 Pro).
9. **Backup ZIP privado:** exporta o banco OCR, favoritos, fichas confirmadas e cópias das fotos; permite restaurar o ZIP no próprio app (substitui dados atuais). Não exige internet.
10. **Exportação CSV** por pesquisa, com categoria de documento, MSC ID e fonte, para conferência externa.

## Documentos analisados durante o planejamento

As fotografias fornecidas mostram:

- Escala geral com colunas separadas para BREAKFAST, LUNCH, DINNER, TAC BREAK e treinamento.
- Folhas por restaurante/setor, frequentemente separando turnos 07:00–12:00 e 13:00–18:00.
- Comunicados de treinamento como `LEARNING AND DEVELOPMENT TRAINING`, incluindo exemplos com `TIME: 10:00` no **Deck 6 — Business Centre** e anúncios com `TIME: 16:00` em outros locais.
- Cadastro com `MSC ID`, `NAME`, `POSITION`, `CABIN`, `DATE EMBARK`: **esta data é de embarque, não da escala de trabalho**.
- Avisos e mudanças de cabine, que não são turnos.

**As fotografias e nomes de funcionários NÃO fazem parte deste ZIP de código-fonte.** Os testes sobre os dados reais serão feitos com imagens importadas pelo próprio usuário no celular.

## Como instalar / compilar

1. Envie este **ZIP original** diretamente ao repositório GitHub com a estrutura preservada: `app/`, `build.gradle`, `settings.gradle`, `.github/workflows/main.yml` na RAIZ.
2. Faça o envio para branch `main`. A aba GitHub **Actions** deve executar **Gerar APK Minha Escala**; também aceita `workflow_dispatch`.
3. Quando o job ficar **verde**, abra o artifact `MinhaEscala-APK`, baixe o ZIP produzido pelo GitHub e extraia `app-debug.apk`.
4. Instale no Android. No POCO X7 Pro com Android 16, a instalação de APK externo exige autorização do sistema.
5. Na interface: **Importar fotos** (seleção múltipla), conferir **Data** e **Tipo** se necessário, digitar nome e tocar **Pesquisar**.

A compilação depende de GitHub Actions com Java 17, Gradle 8.11.1, Android SDK 35 e dependência `com.google.mlkit:text-recognition:16.0.1` (modelo OCR embarcado no APK). **O download das dependências no GitHub usa internet; o app instalado NÃO declara INTERNET e processa o OCR localmente.**

### ATENÇÃO AO ATUALIZAR O APK

O pacote mantém o mesmo `applicationId=com.exemplo.minhaescala` e incrementa `versionCode` para 2, **mas APKs debug compilados em execuções diferentes podem ser assinados por chaves diferentes**. Se o Android apresentar *"Não foi possível instalar"* por assinatura incompatível, a atualização sobre a versão anterior pode falhar. **Não desinstale sem antes salvar os seus dados e certificar-se de que conserva as fotografias originais.** Desinstalar apaga o banco privado do aplicativo. A primeira versão pode não oferecer exportação completa; nesse caso, salve manualmente as fichas confirmadas e mantenha as fotos originais para reimportação. A v2 oferece backup ZIP para futuras migrações.

Para atualizações definitivas que preservem dados entre builds, é necessário assinar as versões com **a mesma chave de assinatura persistente**, armazenada de forma privada (não incluir arquivo de chave em repositório público).

## Limitações de precisão

- As fotografias estão inclinadas, com reflexos, folhas sobrepostas e letras pequenas. OCR pode criar *linhas fictícias*, misturar colunas ou errar horários.
- O modelo ML Kit Latin NÃO identifica automaticamente todas as estruturas de tabela. Não prometemos reconhecer cada célula de forma exata.
- As fichas agrupadas são visualizações de OCR, **não constituem documento oficial nem confirmação de escala**.
- Valores como `00:00`, `07:00`, `12:00` ou `16:00` não devem ser atribuídos a turno/treino sem conferir o cabeçalho e a coluna corretos.
- Quando houver dúvida, use **Ver foto** e **Corrigir / confirmar**. Data do anúncio e data de embarque não são equivalentes.
- Sem internet, sem acesso a sistemas do empregador, sem alertas push automáticos.

## Privacidade

O sistema guarda imagens, OCR, identificação MSC e horários **dentro do armazenamento privado do aparelho**. O projeto não solicita permissão `INTERNET`. **NÃO publique ZIPs de backup, fotografias ou planilhas contendo informações pessoais de colegas em repositórios públicos.**

Consulte `TESTES_V2.md` para os testes guiados.
