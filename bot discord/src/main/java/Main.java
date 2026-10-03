import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.OutputStream;

public class Main extends ListenerAdapter {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final Map<Long, Map<Long, Integer>> userChannelMessages = new HashMap<>();
    private final Map<Long, Map<Long, Long>> userVoiceTimes = new HashMap<>();
    private final Map<Long, Long> voiceJoinTimes = new HashMap<>();
    private final Map<Long, Integer> infractionCounts = new HashMap<>();

    // Liste des mots interdits pour le mute automatique
    private final List<String> forbiddenWords = List.of("motinterdit1", "motinterdit2"); // Remplace par tes mots

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            String response = "Bot is running!";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        });
        server.start();

        String token = System.getenv("DISCORD_TOKEN");
        if (token == null) {
            throw new IllegalArgumentException("La variable d'environnement DISCORD_TOKEN n'est pas définie !");
        }

        JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_VOICE_STATES
                )
                .addEventListeners(new Main())
                .build();
    }

    @Override
    public void onReady(ReadyEvent event) {
        System.out.println("Le bot est connecté sous le nom : " + event.getJDA().getSelfUser().getAsTag());

        Guild guild = event.getJDA().getGuildById("1459654955513413858");

        if (guild != null) {
            guild.updateCommands().addCommands(
                Commands.slash("status", "Vérifie si le bot est en ligne"),
                Commands.slash("bot", "Commandes relatives au bot")
                    .addSubcommands(
                        new SubcommandData("pdp", "Affiche la photo de profil du bot ou d'un membre")
                            .addOption(OptionType.USER, "membre", "Le membre dont tu veux voir la PDP", false)
                    ),
                Commands.slash("avatar", "Affiche l'avatar d'un utilisateur")
                    .addOption(OptionType.USER, "membre", "Le membre dont tu veux voir l'avatar", false),
                Commands.slash("clear", "Supprime les messages envoyés après une heure précise")
                    .addOption(OptionType.STRING, "heure", "Heure cible (ex: 14:30)", true)
                    .addOption(OptionType.STRING, "date", "Date optionnelle (ex: 25/08/2026)", false)
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE)),
                Commands.slash("tempban", "Bannit temporairement un membre")
                    .addOption(OptionType.USER, "membre", "Le membre à bannir", true)
                    .addOption(OptionType.STRING, "duree", "Durée du ban (ex: 2h ou 3d)", true)
                    .addOption(OptionType.STRING, "raison", "Raison du bannissement", true)
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)),
                Commands.slash("unban", "Débannit un membre")
                    .addOption(OptionType.USER, "membre", "Le membre à débannir (ou son ID)", true)
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)),
                Commands.slash("stats", "Affiche tes statistiques d'activité ou celles d'un membre")
                    .addOption(OptionType.USER, "membre", "Le membre dont tu veux voir les stats", false),
                Commands.slash("setup-ticket", "Affiche le panneau avec catégories de tickets")
                    .addOption(OptionType.CHANNEL, "salon", "Salon où afficher le panneau", true)
                    .addOption(OptionType.ROLE, "role-staff", "Rôle du staff ayant accès aux tickets", true)
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
                Commands.slash("setup-tiktok", "Configure les notifications TikTok pour un créateur")
                    .addOption(OptionType.CHANNEL, "salon", "Salon où envoyer les alertes TikTok", true)
                    .addOption(OptionType.STRING, "username", "Nom d'utilisateur TikTok (ex: @moncompte)", true)
                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
            ).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("status")) {
            event.reply("Le bot est en ligne et fonctionnel !").queue();
        }

        if (event.getName().equals("bot")) {
            if ("pdp".equals(event.getSubcommandName())) {
                User target = event.getOption("membre") != null 
                    ? event.getOption("membre").getAsUser() 
                    : event.getJDA().getSelfUser();

                String avatarUrl = target.getEffectiveAvatarUrl() + "?size=1024";

                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Photo de profil de " + target.getName())
                    .setImage(avatarUrl)
                    .setColor(Color.BLUE);

                event.replyEmbeds(embed.build()).queue();
            }
        }

        if (event.getName().equals("avatar")) {
            User target = event.getOption("membre") != null 
                ? event.getOption("membre").getAsUser() 
                : event.getUser();

            String avatarUrl = target.getEffectiveAvatarUrl() + "?size=1024";

            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Avatar de " + target.getName())
                .setImage(avatarUrl)
                .setColor(Color.BLUE);

            event.replyEmbeds(embed.build()).queue();
        }

        if (event.getName().equals("clear")) {
            String heureStr = event.getOption("heure").getAsString();
            String dateStr = event.getOption("date") != null ? event.getOption("date").getAsString() : null;

            try {
                LocalTime time = LocalTime.parse(heureStr, DateTimeFormatter.ofPattern("HH:mm"));
                LocalDate date = dateStr != null 
                    ? LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : LocalDate.now(ZoneId.systemDefault());

                LocalDateTime targetDateTime = LocalDateTime.of(date, time);

                event.deferReply(true).queue();

                TextChannel channel = event.getChannel().asTextChannel();
                
                channel.getIterableHistory().takeAsync(100).thenAccept(messages -> {
                    List<Message> messagesToDelete = new ArrayList<>();

                    for (Message msg : messages) {
                        LocalDateTime msgTime = msg.getTimeCreated().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

                        if (msgTime.isAfter(targetDateTime) || msgTime.isEqual(targetDateTime)) {
                            messagesToDelete.add(msg);
                        }
                    }

                    if (messagesToDelete.isEmpty()) {
                        event.getHook().sendMessage("Aucun message trouvé après l'heure indiquée.").queue();
                    } else {
                        channel.purgeMessages(messagesToDelete);
                        event.getHook().sendMessage("✅ " + messagesToDelete.size() + " message(s) supprimé(s).").queue();
                    }
                });

            } catch (Exception e) {
                event.reply("Format invalide. Utilise `HH:mm` pour l'heure et `JJ/MM/AAAA` pour la date.").setEphemeral(true).queue();
            }
        }

        if (event.getName().equals("tempban")) {
            User targetUser = event.getOption("membre").getAsUser();
            String dureeStr = event.getOption("duree").getAsString().toLowerCase().trim();
            String raison = event.getOption("raison").getAsString();
            Guild guild = event.getGuild();

            if (guild == null) return;

            long millis = parseDurationToMillis(dureeStr);

            if (millis <= 0) {
                event.reply("❌ Format de durée invalide. Utilise par exemple `2h` (2 heures) ou `3d` (3 jours).").setEphemeral(true).queue();
                return;
            }

            String dureeLisible = formatDuration(dureeStr);

            EmbedBuilder embedMp = new EmbedBuilder()
                .setTitle("⛔ Bannissement temporaire")
                .setDescription("Tu as été banni temporairement du serveur **" + guild.getName() + "**.\n\n" +
                                "⏳ **Durée** : " + dureeLisible + "\n" +
                                "📝 **Raison** : " + raison)
                .setThumbnail(targetUser.getEffectiveAvatarUrl())
                .setColor(Color.RED);

            targetUser.openPrivateChannel().queue(privateChannel -> {
                privateChannel.sendMessageEmbeds(embedMp.build()).queue(
                    success -> banAndScheduleUnban(event, guild, targetUser, millis, dureeLisible, raison),
                    error -> banAndScheduleUnban(event, guild, targetUser, millis, dureeLisible, raison)
                );
            });
        }

        if (event.getName().equals("unban")) {
            User targetUser = event.getOption("membre").getAsUser();
            Guild guild = event.getGuild();

            if (guild == null) return;

            event.deferReply(true).queue();

            guild.unban(targetUser).queue(
                success -> {
                    event.getHook().sendMessage("🔓 **" + targetUser.getName() + "** a été débanni avec succès.").queue();

                    TextChannel logChannel = guild.getTextChannelsByName("logs-admin", true)
                            .stream().findFirst().orElse(null);
                    if (logChannel != null) {
                        logChannel.sendMessage("🔓 **Unban manuel** : " + targetUser.getAsMention() + " a été débanni par " + event.getUser().getAsMention() + ".").queue();
                    }
                },
                error -> event.getHook().sendMessage("❌ Impossible de débannir cet utilisateur. Vérifie qu'il soit bien banni.").queue()
            );
        }

        if (event.getName().equals("stats")) {
            Member targetMember = event.getOption("membre") != null 
                ? event.getOption("membre").getAsMember() 
                : event.getMember();

            if (targetMember == null) {
                event.reply("❌ Impossible de trouver ce membre sur le serveur.").setEphemeral(true).queue();
                return;
            }

            event.deferReply().queue();

            long userId = targetMember.getIdLong();

            Map<Long, Integer> userMsgs = userChannelMessages.getOrDefault(userId, new HashMap<>());
            int totalMsgs = userMsgs.values().stream().mapToInt(Integer::intValue).sum();

            long topTextChannelId = userMsgs.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(-1L);

            String topTextName = "Aucun";
            int topTextCount = 0;
            if (topTextChannelId != -1L) {
                TextChannel ch = event.getGuild().getTextChannelById(topTextChannelId);
                if (ch != null) topTextName = "#" + ch.getName();
                topTextCount = userMsgs.get(topTextChannelId);
            }

            Map<Long, Long> userVoices = userVoiceTimes.getOrDefault(userId, new HashMap<>());
            long totalVoiceMillis = userVoices.values().stream().mapToLong(Long::longValue).sum();

            if (voiceJoinTimes.containsKey(userId)) {
                totalVoiceMillis += (System.currentTimeMillis() - voiceJoinTimes.get(userId));
            }

            long topVoiceChannelId = userVoices.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(-1L);

            String topVoiceName = "Aucun";
            long topVoiceMillis = 0;
            if (topVoiceChannelId != -1L) {
                var ch = event.getGuild().getVoiceChannelById(topVoiceChannelId);
                if (ch != null) topVoiceName = ch.getName();
                topVoiceMillis = userVoices.get(topVoiceChannelId);
            }

            long minutesTotal = TimeUnit.MILLISECONDS.toMinutes(totalVoiceMillis) % 60;
            long heuresTotal = TimeUnit.MILLISECONDS.toHours(totalVoiceMillis);
            String tempsVocalTotal = heuresTotal + "h " + minutesTotal + "m";

            long topVoiceMin = TimeUnit.MILLISECONDS.toMinutes(topVoiceMillis) % 60;
            long topVoiceH = TimeUnit.MILLISECONDS.toHours(topVoiceMillis);
            String topVoiceTimeStr = topVoiceH + "h " + topVoiceMin + "m";

            try {
                byte[] imageBytes = generateStatsCard(
                    targetMember,
                    String.valueOf(totalMsgs),
                    tempsVocalTotal,
                    topTextName,
                    topTextCount + " messages",
                    topVoiceName,
                    topVoiceTimeStr
                );

                event.getHook().sendFiles(FileUpload.fromData(imageBytes, "stats.png")).queue();

            } catch (Exception e) {
                e.printStackTrace();
                event.getHook().sendMessage("❌ Erreur lors de la génération du graphique des statistiques.").queue();
            }
        }

        if (event.getName().equals("setup-ticket")) {
            TextChannel targetChannel = event.getOption("salon").getAsChannel().asTextChannel();
            Role staffRole = event.getOption("role-staff").getAsRole();

            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎫 Centre d'Assistance & Tickets")
                .setDescription("Besoin d'aide ou de contacter l'équipe du serveur ?\n\n" +
                                "📌 **Sélectionnez la raison de votre demande ci-dessous** pour ouvrir un ticket privé.")
                .setColor(Color.CYAN)
                .setFooter("Système de Ticket • " + event.getGuild().getName(), event.getGuild().getIconUrl());

            StringSelectMenu selectMenu = StringSelectMenu.create("ticket_reason_select:" + staffRole.getId())
                .setPlaceholder("👉 Choisissez la raison de votre ticket...")
                .addOption("❓ Question / Aide", "aide", "Vous avez une question générale ou besoin d'assistance.")
                .addOption("🚨 Signalement", "signalement", "Signaler un joueur, un problème ou un comportement.")
                .addOption("💼 Recrutement / Partenariat", "recrutement", "Proposer une candidature ou un partenariat.")
                .addOption("📝 Autre demande", "autre", "Pour tout autre sujet particulier.")
                .build();

            targetChannel.sendMessageEmbeds(embed.build()).setActionRow(selectMenu).queue(
                success -> event.reply("✅ Panneau de tickets configuré avec succès dans " + targetChannel.getAsMention()).setEphemeral(true).queue(),
                error -> event.reply("❌ Erreur lors de la configuration.").setEphemeral(true).queue()
            );
        }

        if (event.getName().equals("setup-tiktok")) {
            TextChannel targetChannel = event.getOption("salon").getAsChannel().asTextChannel();
            String username = event.getOption("username").getAsString();

            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎵 Notifications TikTok configurées")
                .setDescription("Les alertes pour le compte **" + username + "** ont été activées dans ce salon !\n\n" +
                                "⚠ *Note : Les notifications automatiques s'afficheront ici dès qu'une nouvelle vidéo sera publiée.*")
                .setColor(new Color(254, 44, 85))
                .setFooter("TikTok Tracker • " + event.getGuild().getName());

            targetChannel.sendMessageEmbeds(embed.build()).queue(
                success -> event.reply("✅ Système TikTok configuré avec succès dans " + targetChannel.getAsMention() + " pour **" + username + "** !").setEphemeral(true).queue(),
                error -> event.reply("❌ Erreur lors de la configuration TikTok.").setEphemeral(true).queue()
            );
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.getComponentId().startsWith("ticket_reason_select:")) {
            String staffRoleId = event.getComponentId().split(":")[1];
            String selectedReason = event.getValues().get(0);

            String reasonLabel = switch (selectedReason) {
                case "aide" -> "Question / Aide";
                case "signalement" -> "Signalement";
                case "recrutement" -> "Recrutement / Partenariat";
                default -> "Autre demande";
            };

            TextInput subjectInput = TextInput.create("ticket_subject", "Sujet principal", TextInputStyle.SHORT)
                    .setPlaceholder("Ex: Problème avec un rôle / Question sur les règles")
                    .setRequiredRange(3, 100)
                    .setRequired(true)
                    .build();

            TextInput descriptionInput = TextInput.create("ticket_description", "Explication détaillée", TextInputStyle.PARAGRAPH)
                    .setPlaceholder("Décrivez votre demande en détail ici...")
                    .setRequiredRange(10, 1000)
                    .setRequired(true)
                    .build();

            Modal modal = Modal.create("ticket_modal:" + selectedReason + ":" + staffRoleId, "Ticket : " + reasonLabel)
                    .addActionRow(subjectInput)
                    .addActionRow(descriptionInput)
                    .build();

            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().startsWith("ticket_modal:")) {
            String[] parts = event.getModalId().split(":");
            String reasonType = parts[1];
            String staffRoleId = parts[2];

            String subject = event.getValue("ticket_subject").getAsString();
            String description = event.getValue("ticket_description").getAsString();

            Guild guild = event.getGuild();
            Member member = event.getMember();
            if (guild == null || member == null) return;

            String channelName = "ticket-" + member.getUser().getName().toLowerCase();

            boolean ticketExists = guild.getTextChannels().stream()
                    .anyMatch(c -> c.getName().equals(channelName));

            if (ticketExists) {
                event.reply("❌ Tu as déjà un ticket ouvert !").setEphemeral(true).queue();
                return;
            }

            event.deferReply(true).queue();

            Role staffRole = guild.getRoleById(staffRoleId);

            List<Permission> allowPerms = List.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_ATTACH_FILES);
            List<Permission> denyPerms = List.of(Permission.VIEW_CHANNEL);

            String reasonTitle = switch (reasonType) {
                case "aide" -> "❓ Question / Aide";
                case "signalement" -> "🚨 Signalement";
                case "recrutement" -> "💼 Recrutement / Partenariat";
                default -> "📝 Autre demande";
            };

            guild.createTextChannel(channelName)
                .addPermissionOverride(guild.getPublicRole(), null, denyPerms)
                .addPermissionOverride(member, allowPerms, null)
                .queue(ticketChannel -> {
                    if (staffRole != null) {
                        ticketChannel.upsertPermissionOverride(staffRole).setAllowed(allowPerms).queue();
                    }

                    EmbedBuilder embedTicket = new EmbedBuilder()
                        .setTitle("🎫 Ticket : " + subject)
                        .setColor(Color.GREEN)
                        .addField("👤 Membre", member.getAsMention(), true)
                        .addField("📌 Catégorie", reasonTitle, true)
                        .addField("📄 Description", description, false)
                        .setFooter("Pour fermer ce ticket, cliquez sur le bouton ci-dessous.");

                    Button closeButton = Button.danger("ticket_close", "🔒 Fermer le ticket");

                    ticketChannel.sendMessage(member.getAsMention() + (staffRole != null ? " " + staffRole.getAsMention() : ""))
                        .setEmbeds(embedTicket.build())
                        .setActionRow(closeButton)
                        .queue();

                    event.getHook().sendMessage("✅ Ton ticket a été créé avec succès : " + ticketChannel.getAsMention()).queue();
                });
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().equals("ticket_close")) {
            event.reply("🔒 Ce ticket sera fermé et supprimé dans 5 secondes...").queue();

            scheduler.schedule(() -> {
                if (event.getChannel() != null) {
                    event.getChannel().delete().queue();
                }
            }, 5, TimeUnit.SECONDS);
        }
    }

    // ==========================================
    // FILTRE AUTOMATIQUE (MOTS INTERDITS + LIENS)
    // ==========================================
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }

        Member member = event.getMember();
        if (member == null) return;

        // Laisser passer les admins et modérateurs
        if (member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.MESSAGE_MANAGE)) {
            return;
        }

        String messageContent = event.getMessage().getContentRaw();
        String lowerCaseContent = messageContent.toLowerCase();

        // 1. Vérification des mots interdits (Mute automatique)
        boolean containsForbiddenWord = forbiddenWords.stream().anyMatch(lowerCaseContent::contains);
        if (containsForbiddenWord) {
            event.getMessage().delete().queue(
                success -> {
                    // Application du timeout/mute (par exemple 10 minutes)
                    long durationMillis = TimeUnit.MINUTES.toMillis(10);
                    member.timeoutFor(durationMillis, TimeUnit.MILLISECONDS).reason("Utilisation de mots interdits").queue(
                        timeoutSuccess -> {
                            event.getChannel().sendMessage(member.getAsMention() + " ❌ Tu as été mutes 10 minutes pour utilisation de mots interdits.")
                                .queue(msg -> scheduler.schedule(() -> msg.delete().queue(s -> {}, e -> {}), 5, TimeUnit.SECONDS));
                        },
                        timeoutError -> {}
                    );
                },
                error -> {}
            );
            return;
        }

        // 2. Laisser passer si le texte contient un lien GIF ou l'extension .gif
        boolean isGifLink = messageContent.contains("tenor.com") 
                         || messageContent.contains("giphy.com") 
                         || messageContent.contains(".gif");

        boolean hasGifEmbed = !event.getMessage().getEmbeds().isEmpty() && 
                              event.getMessage().getEmbeds().stream().anyMatch(e -> e.getImage() != null);

        if (isGifLink || hasGifEmbed) {
            return; 
        }

        // 3. Bloquer les autres liens et invitations Discord
        Pattern linkPattern = Pattern.compile("(?i)\\b((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]|www\\.[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]|discord\\.gg/[a-zA-Z0-9]+|discord(app)?\\.com/invite/[a-zA-Z0-9]+)\\b");
        Matcher matcher = linkPattern.matcher(messageContent);

        if (matcher.find()) {
            event.getMessage().delete().queue(
                success -> {
                    event.getChannel().sendMessage(member.getAsMention() + " ❌ Les liens ne sont pas autorisés sur ce serveur !")
                        .queue(msg -> {
                            scheduler.schedule(() -> msg.delete().queue(s -> {}, e -> {}), 5, TimeUnit.SECONDS);
                        });
                },
                error -> {}
            );
        }
    }

    private byte[] generateStatsCard(
        Member member, 
        String messages, 
        String voiceTime, 
        String topTextName, 
        String topTextDetail, 
        String topVoiceName, 
        String topVoiceDetail
    ) throws Exception {
        int width = 720;
        int height = 360;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new Color(28, 29, 34));
        g.fill(new RoundRectangle2D.Float(0, 0, width, height, 24, 24));

        BufferedImage avatar;
        try {
            URL url = new URL(member.getUser().getEffectiveAvatarUrl() + "?size=128");
            avatar = ImageIO.read(url);
        } catch (Exception e) {
            avatar = new BufferedImage(54, 54, BufferedImage.TYPE_INT_ARGB);
        }

        BufferedImage circularAvatar = new BufferedImage(54, 54, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gAvatar = circularAvatar.createGraphics();
        gAvatar.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gAvatar.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, 54, 54));
        gAvatar.drawImage(avatar, 0, 0, 54, 54, null);
        gAvatar.dispose();

        g.drawImage(circularAvatar, 20, 20, null);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString(member.getEffectiveName(), 86, 42);

        String serverName = member.getGuild().getName();
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(235, 180, 50));
        g.drawString(serverName, 86, 62);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH);
        String createdDate = member.getUser().getTimeCreated().format(formatter);
        String joinedDate = member.getTimeJoined().format(formatter);

        drawHeaderBadge(g, 460, 20, "Créé le", createdDate);
        drawHeaderBadge(g, 585, 20, "Rejoint le", joinedDate);

        drawContainer(g, 20, 90, 215, 120);
        g.setColor(new Color(170, 175, 185));
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("Classements", 32, 112);
        g.setColor(new Color(235, 180, 50));
        g.drawString("🏆", 205, 112);

        drawRowItem(g, 32, 125, 191, 32, "Messages", "#3");
        drawRowItem(g, 32, 165, 191, 32, "Vocal", "#4");

        drawContainer(g, 252, 90, 215, 120);
        g.setColor(new Color(170, 175, 185));
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("Messages", 264, 112);
        g.setColor(new Color(87, 242, 135));
        g.drawString("💬", 437, 112);

        drawStatBreakdownRow(g, 264, 125, 191, 24, "1j", messages + " msgs");
        drawStatBreakdownRow(g, 264, 153, 191, 24, "7j", messages + " msgs");
        drawStatBreakdownRow(g, 264, 181, 191, 24, "30j", messages + " msgs");

        drawContainer(g, 484, 90, 216, 120);
        g.setColor(new Color(170, 175, 185));
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("Activité Vocale", 496, 112);
        g.setColor(new Color(235, 69, 158));
        g.drawString("🔊", 670, 112);

        drawStatBreakdownRow(g, 496, 125, 192, 24, "1j", voiceTime);
        drawStatBreakdownRow(g, 496, 153, 192, 24, "7j", voiceTime);
        drawStatBreakdownRow(g, 496, 181, 192, 24, "30j", voiceTime);

        drawContainer(g, 20, 220, 680, 105);
        g.setColor(new Color(170, 175, 185));
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("Salons principaux & Activité", 32, 242);

        drawChannelRow(g, 32, 255, 320, 28, "💬 " + topTextName, topTextDetail, new Color(87, 242, 135));
        drawChannelRow(g, 32, 288, 320, 28, "🔊 " + topVoiceName, topVoiceDetail, new Color(235, 69, 158));

        g.setColor(new Color(120, 125, 135));
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.drawString("Serveur : " + serverName + " — Fuseau : UTC", 20, 345);

        g.setColor(new Color(87, 242, 135));
        g.fillOval(570, 337, 8, 8);
        g.setColor(Color.WHITE);
        g.drawString("Message", 583, 345);

        g.setColor(new Color(235, 69, 158));
        g.fillOval(645, 337, 8, 8);
        g.setColor(Color.WHITE);
        g.drawString("Vocal", 658, 345);

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private void drawContainer(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(38, 40, 46));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 14, 14));
    }

    private void drawHeaderBadge(Graphics2D g, int x, int y, String title, String value) {
        g.setColor(new Color(42, 44, 51));
        g.fill(new RoundRectangle2D.Float(x, y, 115, 42, 10, 10));

        g.setColor(new Color(150, 155, 165));
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.drawString(title, x + 8, y + 14);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        
        String displayedValue = value;
        if (displayedValue.length() > 14) {
            displayedValue = displayedValue.substring(0, 12) + "..";
        }
        
        g.drawString(displayedValue, x + 8, y + 32);
    }

    private void drawRowItem(Graphics2D g, int x, int y, int w, int h, String label, String value) {
        g.setColor(new Color(20, 21, 25));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 8, 8));

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(label, x + 10, y + 20);

        g.setColor(new Color(200, 205, 215));
        g.drawString(value, x + w - 30, y + 20);
    }

    private void drawStatBreakdownRow(Graphics2D g, int x, int y, int w, int h, String tag, String val) {
        g.setColor(new Color(20, 21, 25));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 6, 6));

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString(tag, x + 10, y + 16);

        g.setColor(new Color(180, 185, 195));
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString(val, x + 40, y + 16);
    }

    private void drawChannelRow(Graphics2D g, int x, int y, int w, int h, String channel, String count, Color accentColor) {
        g.setColor(new Color(20, 21, 25));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 6, 6));

        g.setColor(accentColor);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(channel, x + 10, y + 18);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString(count, x + w - 90, y + 18);
    }

    private long parseDurationToMillis(String input) {
        Pattern pattern = Pattern.compile("^(\\d+)([hd])$");
        Matcher matcher = pattern.matcher(input);

        if (!matcher.matches()) return -1;

        long val = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        if (unit.equals("h")) {
            return TimeUnit.HOURS.toMillis(val);
        } else if (unit.equals("d")) {
            return TimeUnit.DAYS.toMillis(val);
        }
        return -1;
    }

    private String formatDuration(String input) {
        return input.replace("h", " heure(s)").replace("d", " jour(s)");
    }

    private void banAndScheduleUnban(SlashCommandInteractionEvent event, Guild guild, User targetUser, long millis, String dureeLisible, String raison) {
        guild.ban(targetUser, 0, java.util.concurrent.TimeUnit.SECONDS).reason(raison).queue(
            success -> {
                event.reply("⛔ **" + targetUser.getName() + "** a été banni temporairement pour **" + dureeLisible + "**.").setEphemeral(true).queue();

                TextChannel logChannel = guild.getTextChannelsByName("logs-admin", true)
                        .stream().findFirst().orElse(null);
                if (logChannel != null) {
                    logChannel.sendMessage("⛔ **Tempban** : " + targetUser.getAsMention() + " a été banni par " + event.getUser().getAsMention() + ".\n⏳ **Durée** : " + dureeLisible + "\n📝 **Raison** : " + raison).queue();
                }

                scheduler.schedule(() -> {
                    guild.unban(targetUser).queue(
                        s -> {
                            if (logChannel != null) {
                                logChannel.sendMessage("🔓 **Fin du Tempban** : " + targetUser.getAsMention() + " a été automatiquement débanni.").queue();
                            }
                        },
                        e -> {}
                    );
                }, millis, TimeUnit.MILLISECONDS);
            },
            error -> event.reply("❌ Impossible de bannir ce membre. Vérifie les permissions du bot.").setEphemeral(true).queue()
        );
    }
}
