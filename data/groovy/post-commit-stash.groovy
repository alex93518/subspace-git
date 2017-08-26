@Grab(group='mysql', module='mysql-connector-java', version='5.1.41')
@Grab(group='com.google.firebase', module='firebase-admin', version='5.2.0')

import com.gitblit.utils.JGitUtils
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.ReceiveCommand.Result
import org.eclipse.jgit.transport.ReceiveCommand.Type
import org.slf4j.Logger
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource
import com.mysql.jdbc.Connection
import com.mysql.jdbc.Statement
import java.sql.ResultSet

import java.util.HashMap
import java.io.FileInputStream

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebaseOptions.Builder
import com.google.firebase.auth.FirebaseCredentials
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

logger.info("commit-stash hook triggered by ${user.username} for ${repository.name}")

Repository r = gitblit.getRepository(repository.name)

String[] repoData = repository.name.split("/")
String username = repoData[0].replaceFirst("~", "")
println(username)
String gitname = repoData[1].split("\\.")[0]

for(command in commands) {
  if (command.getResult() == Result.OK && command.getType() == Type.UPDATE) {
    MysqlDataSource dataSource = new MysqlDataSource();
    dataSource.setDatabaseName("subspace")
    dataSource.setUser("subspace")
    dataSource.setPassword("subspace")
    def host = System.getenv("DB_HOST") ?: "subspace.cjlasbongr01.us-east-1.rds.amazonaws.com"
    dataSource.setServerName(host)

    Connection conn = dataSource.getConnection()
    
    stmt = conn.prepareStatement("select id from Users where user_name = '${username}'");
    rs = stmt.executeQuery()
    while (rs.next()) {
      userId = rs.getString(1)
    }

    stmt = conn.prepareStatement("select id, is_push_vote, is_review_stash from Repositories where name = '${gitname}' and owner_id = '${userId}'")
    rs = stmt.executeQuery()
    while (rs.next()) {
      repoId = rs.getString(1)
      isPushVote = rs.getBoolean(2)
      isReviewStash = rs.getBoolean(3)
    }

    if (repoId && isPushVote) {
      String oldhash = command.getOldId().getName()
      String newhash = command.getNewId().getName()

      stmt = conn.prepareStatement("select id, is_merged, stash_num from Stashes where repository_id = '${repoId}' and base_oid = '${oldhash}' and current_oid = '${newhash}'")
      rs = stmt.executeQuery()
      if (!rs.next()) {
        stmt = conn.prepareStatement("select MAX(stash_num) from Stashes where repository_id = '${repoId}'")
        rs = stmt.executeQuery()
        maxStash = 0
        while (rs.next()) {
          maxStash = rs.getInt(1)
        }
        maxStash += 1
        String newBranch = "stash-${maxStash}"
        JGitUtils.createOrphanBranch(r, newBranch, null)
        JGitUtils.setBranchRef(r, newBranch, newhash)
        JGitUtils.setBranchRef(r, r.getBranch(), oldhash)

        stashId = java.util.UUID.randomUUID().toString()
        if (isReviewStash) {
          isOnline = 0
        } else {
          isOnline = 1
        }
        stmt = conn.prepareStatement("insert into Stashes values (?, ?, ?, ?, ?, ?,  0, 3.0, ?, NULL, NULL, now(), now())")
        stmt.setString(1, stashId)
        stmt.setString(2, userId)
        stmt.setString(3, repoId)
        stmt.setInt(4, maxStash)
        stmt.setString(5, oldhash)
        stmt.setString(6, newhash)
        stmt.setInt(7, isOnline)
        rs = stmt.executeUpdate()

        if (isReviewStash) {
          println(FirebaseApp.getApps())
          if (FirebaseApp.getApps() == []) {
            FileInputStream serviceAccount = new FileInputStream("./firebase.json");
            FirebaseOptions options = new FirebaseOptions.Builder()
              .setCredential(FirebaseCredentials.fromCertificate(serviceAccount))
              .setDatabaseUrl("https://terella-52845.firebaseio.com/")
              .build();
            FirebaseApp.initializeApp(options);
          }

          HashMap msgData = new HashMap();
          msgData.put("message", "A push need to be reviewed (#" + newBranch + ")")
          msgData.put("operation", "stashReview")
          msgData.put("stashName", newBranch)
          DatabaseReference ref = FirebaseDatabase
            .getInstance()
            .getReference("notifications/" + username);
          ref.push().setValue(msgData)
          DatabaseReference refMeta = FirebaseDatabase
            .getInstance()
            .getReference("notificationsMeta/" + username);
          refMeta.child("unread").setValue(true);
        }

        logger.info("oldhash: ${oldhash}, newhash: ${newhash}")
      } else {
        isMerged = rs.getBoolean(2)
        if (isMerged) {
          stashNum = rs.getInt(3)
          stashName = "refs/heads/stash-" + stashNum
          JGitUtils.deleteBranchRef(r, stashName)
          stmt = conn.prepareStatement("delete from Stashes where repository_id = '${repoId}' and base_oid = '${oldhash}' and current_oid = '${newhash}' and stash_num = ${stashNum}")
          rs = stmt.executeUpdate()
        } else {
          JGitUtils.setBranchRef(r, r.getBranch(), oldhash)
          rs.close()
        }
      }
    } else {
      rs.close()
    }

    stmt.close()
    conn.close()
  }
}

r.close()
