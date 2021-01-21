package red.man10.bungee.manager

import net.md_5.bungee.api.connection.ProxiedPlayer
import red.man10.bungee.manager.Man10BungeePlugin.Companion.plugin
import red.man10.bungee.manager.db.MySQLManager
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class PlayerData(val uuid: UUID,val mcid: String) {

    constructor(p:ProxiedPlayer) : this(p.uniqueId,p.name)

    var freezeUntil: Date? = null      //      拘束期限
    var muteUntil: Date? = null        //      ミュート期限
    var jailUntil: Date? = null        //      ジェイル期限
    var banUntil: Date? = null         //      BAN期限

    private var score:Int = 0                  //      スコア


    fun isFrozen() : Boolean{
        if(freezeUntil == null)return false

        return true
    }
    fun isMuted() : Boolean{
        if(muteUntil == null)return false

        return true
    }
    fun isJailed() : Boolean{
        if(jailUntil == null)return false

        return true
    }
    fun isBanned() : Boolean{
        if(banUntil == null)return false

        return true
    }
    //      ミュート時間を追加
    fun addMuteTime(min:Int=30,hour:Int=0,day:Int=0){

        muteUntil = addDate(muteUntil,min,hour,day)
        save()
    }

    fun addFrozenTime(min:Int=30,hour:Int=0,day:Int=0){

        freezeUntil = addDate(freezeUntil,min,hour,day)
        save()
    }

    fun addJailTime(min:Int=30,hour:Int=0,day:Int=0){

        jailUntil = addDate(jailUntil,min,hour,day)
        save()
    }

    fun addBanTime(min:Int=30,hour:Int=0,day:Int=0){

        banUntil = addDate(banUntil,min,hour,day)
        save()

    }

    fun resetMute(){
        muteUntil = null
        save()
    }

    fun resetBan(){
        banUntil = null
        save()
    }

    fun resetJail(){
        jailUntil = null
        save()
    }

    fun addDate(date:Date?,min:Int,hour:Int,day:Int): Date? {

        val calender = Calendar.getInstance()

        calender.time = date?:Date()
        calender.add(Calendar.MINUTE,min)
        calender.add(Calendar.HOUR,hour)
        calender.add(Calendar.DATE,day)

        val time = calender.time

        if (time.time<Date().time){
            return null
        }

        return time
    }

    fun addScore(int: Int){
        score += int
        setScore(score)
    }

    fun takeScore(int:Int){
        score -= int
        setScore(score)
    }

    fun setScore(int:Int){
        score = int
        save()
    }

    fun getScore():Int{
        return score
    }


    init {
        load()

        val now = Date()

        if (jailUntil!=null && now>jailUntil)jailUntil = null
        if (muteUntil!=null && now>muteUntil)muteUntil = null
        if (banUntil !=null && now>banUntil)banUntil = null
        if (freezeUntil != null && now>freezeUntil)freezeUntil = null

        save()

        plugin.logger.info("Loaded ${mcid}'s player data ")
    }

    fun load(){

        val mysql = MySQLManager(plugin,"BungeeManager Loading")

        val rs = mysql.query("SELECT * from player_data where uuid='$uuid';")

        if (rs == null || !rs.next()){

            mysql.execute("INSERT INTO player_data (uuid, mcid, freeze_until, mute_until, jail_until, ban_until, score) " +
                    "VALUES ('$uuid', '$mcid', null, null, null, null, DEFAULT)")

            plugin.logger.info("create $mcid's data.")

            mysql.close()

            return
        }

        jailUntil = rs.getDate("jail_until")?:null
        banUntil = rs.getDate("ban_until")?:null
        freezeUntil = rs.getDate("freeze_until")?:null
        muteUntil = rs.getDate("mute_until")?:null

        score = rs.getInt("score")

        mysql.close()
        rs.close()
    }

    fun save(){

        MySQLManager.executeQueue("UPDATE player_data SET " +
                "mcid='$mcid'," +
                "freeze_until=${dateToDatetime(freezeUntil)}," +
                "mute_until=${dateToDatetime(muteUntil)}," +
                "jail_until=${dateToDatetime(jailUntil)}," +
                "ban_until=${dateToDatetime(banUntil)}," +
                "score=$score " +
                "where uuid='${uuid}';")

    }

    fun saveCommand(command:String){
        MySQLManager.executeQueue("INSERT INTO command_log (uuid, mcid, command, date) VALUES ('$uuid', '$mcid', '$command', ${dateToDatetime(Date())});")
    }

    fun saveMessage(message:String){
        MySQLManager.executeQueue("INSERT INTO message_log (uuid, mcid, message, date) VALUES ('$uuid', '$mcid', '$message', ${dateToDatetime(Date())});")
    }


    //mysql datetime を保存するやつ
    fun dateToDatetime(date: Date?): String? {
        return "'${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date?:return null)}'"
    }


    companion object{

        private val mysql = MySQLManager(plugin,"BungeeManager Get UUID")

        //mcidからuuidを取得する
        fun getUUID(mcid:String):UUID?{

            var uuid = plugin.proxy.getPlayer(mcid)?.uniqueId

            if (uuid ==null){

                val rs = mysql.query("select uuid from player_data where mcid='$mcid';")

                if (rs ==null){
                    mysql.close()
                    return null
                }

                rs.next()
                uuid = UUID.fromString(rs.getString("uuid"))
                rs.close()
                mysql.close()
            }

            return uuid
        }

        //プレイヤー名からユーザーデータをつくる
        fun get(mcid:String): Pair<PlayerData,UUID>? {
            val uuid = getUUID(mcid)?:return null
            return Pair(PlayerData(uuid,mcid),uuid)
        }
    }
}