package com.threatintelligence.entity.transform.jobs;

import com.sdk.threatwinds.entity.ein.ThreatIntEntity;
import com.threatintelligence.storage.SQLiteConnection;
import com.threatintelligence.utilities.UtilitiesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sdk.threatwinds.enums.TWEndPointEnum;
import com.sdk.threatwinds.factory.RequestFactory;
import com.sdk.threatwinds.interfaces.IRequestExecutor;
import com.sdk.threatwinds.service.bridge.WebClientService;
import com.threatintelligence.config.EnvironmentConfig;
import com.threatintelligence.entity.ein.github.yara.GHYaraExtractor;
import com.threatintelligence.enums.FeedTypeEnum;
import com.threatintelligence.enums.FlowPhasesEnum;
import com.threatintelligence.enums.LogTypeEnum;
import com.threatintelligence.factory.TWTransformationFactory;
import com.threatintelligence.interfaces.IEntityTransform;
import com.threatintelligence.interfaces.IJobExecutor;
import com.threatintelligence.interfaces.IProcessor;
import com.threatintelligence.json.parser.GenericParser;
import com.threatintelligence.logging.LogDef;
import com.threatintelligence.readers.FileStreamReader;
import com.threatintelligence.scraper.LinkListGenerator;
import com.threatintelligence.scraper.LinkPage;
import com.threatintelligence.urlcreator.FullPathUrlCreator;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Used to process data from a github repository
 * */
public class GHJob implements IJobExecutor {
    private final Logger log = LoggerFactory.getLogger(GHJob.class);
    private static final String CLASSNAME = "GHJob";
    private static WebClientService webClientService;
    private static List<ThreatIntEntity> threatIntEntityList;
    private static SQLiteConnection sqLiteConnection;

    public GHJob() {
        try {
            this.sqLiteConnection = new SQLiteConnection();
        } catch (SQLException ex) {
            log.info("There was errors with local storage, " +
                    "please check your SQLite configuration, error: " + ex.getLocalizedMessage());
        }
    }

    @Override
    public void executeFlow() throws Exception {
        final String ctx = CLASSNAME + ".executeGitHub";
        String feedSelected = EnvironmentConfig.FEED_FORMAT;
        threatIntEntityList = new ArrayList<>();
        webClientService = new WebClientService().withAPIUrl("").withKey("").withSecret("").buildClient();

        // ----------------------- Log the process init -------------------------//
        log.info(ctx + ": " + new LogDef(LogTypeEnum.TYPE_EXECUTION.getVarValue(), feedSelected,
                FlowPhasesEnum.P0_BEGIN_PROCESS.getVarValue()).logDefToString());

        // ----------------------- Log the feed scrap to search for links -------------------------//
        try {
            // RFXN is a direct resource and don't have content-type, so, the feed url have to be inserted directly
            // in the list of links
            if (FeedTypeEnum.TYPE_RFXN_YARA.getVarValue().compareToIgnoreCase(EnvironmentConfig.FEED_FORMAT) == 0) {
                LinkPage.getListOfLinks().add(EnvironmentConfig.FEED_URL);
            }
            // If other yara, perform the recursive path scan
            else {
                IProcessor lpro = new LinkListGenerator();
                lpro.process(EnvironmentConfig.FEED_URL);
            }
        } catch (Exception ex) {
            log.error(ctx + ": " + new LogDef(LogTypeEnum.TYPE_ERROR.getVarValue(),
                    feedSelected, "Problem getting data from host: " + ex.getLocalizedMessage()).logDefToString());
        }

        //First we create fixed thread pool executor with EnvironmentConfig.THREAD_POOL_SIZE threads, one per file
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(EnvironmentConfig.THREAD_POOL_SIZE);

        //--------------------------------The concurrent ETL process is here-------------------------------------------
        while (LinkPage.getListOfLinks().size() > 0) {
            executor.execute(new GitHubParallelTask((String) LinkPage.getListOfLinks().remove(0)));
        }

        //Thread end is called
        executor.shutdown();
        //Wait 1 sec until termination
        while (!executor.isTerminated()) {
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //------------------------ Cleaning the list, avoiding reinsert entities already inserted--------------//
        List<ThreatIntEntity> cleanedList = UtilitiesService.cleanInsertedEntities(this.sqLiteConnection,
                GHJob.threatIntEntityList);

        // Initializing the error list during post to endpoints
        List<ThreatIntEntity> updateDBList = new ArrayList<>();

        // ----------------------- Inserting via sdk -------------------------//
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(EnvironmentConfig.THREAD_POOL_SIZE);
        IRequestExecutor mainJob = new RequestFactory(1).withThreadPoolExecutor(executor).getExecutor();
        if (mainJob != null) {
            log.info(ctx + " - Begin batch execution for " + cleanedList.size() + " entities");
            updateDBList = (List<ThreatIntEntity>)mainJob.executeRequest(TWEndPointEnum.POST_ENTITIES.get(), cleanedList, webClientService);
        }

        //------------------------ After posting the entities, perform database update of inserted ones --------//
        UtilitiesService.cleanErrorEntitiesAndUpdateDB(updateDBList, cleanedList,this.sqLiteConnection);

        log.info(ctx + ": " + new LogDef(LogTypeEnum.TYPE_EXECUTION.getVarValue(), feedSelected,
                FlowPhasesEnum.PN_END_PROCESS.getVarValue()).logDefToString());
    }

    public class GitHubParallelTask implements Runnable {

        String link;

        public GitHubParallelTask(String link) {
            this.link = link;
        }

        @Override
        public void run() {
            final String ctx = CLASSNAME + ".parallelGitHubExecutor";
            FileStreamReader reader = new FileStreamReader();
            String linkToProcess = "";

            try {
                // ----------------------- Log and execute the file reading from internet -------------------------//
                linkToProcess = this.link;
                log.info(ctx + ": " + new LogDef(LogTypeEnum.TYPE_EXECUTION.getVarValue(), linkToProcess,
                        FlowPhasesEnum.P1_READ_FILE.getVarValue()).logDefToString());

                // ----------------------- Log and execute mapping from JSON file to class -------------------------//
                log.info(ctx + ": " + new LogDef(LogTypeEnum.TYPE_EXECUTION.getVarValue(), linkToProcess,
                        FlowPhasesEnum.P2_MAP_JSON_TO_CLASS.getVarValue()).logDefToString());

                // Defining object to extract from source
                Object githubObjects;
                Object dataFromFile;
                if (FeedTypeEnum.TYPE_GITHUB_SURICATA.getVarValue().compareToIgnoreCase(EnvironmentConfig.FEED_FORMAT) == 0){
                    //Suricata rules
                    dataFromFile = reader.readFileAsList(
                            new FullPathUrlCreator().createURL(linkToProcess, EnvironmentConfig.LINK_SEPARATOR)
                    );
                    githubObjects = dataFromFile;
                } else {
                    dataFromFile = reader.readFile(
                            new FullPathUrlCreator().createURL(linkToProcess, EnvironmentConfig.LINK_SEPARATOR)
                    );
                    githubObjects = new GHYaraExtractor((String)dataFromFile).getYaraRuleObjects();
                }

                // ----------------------- Log and execute transformation to Entity class -------------------------//
                log.info(ctx + ": " + new LogDef(LogTypeEnum.TYPE_EXECUTION.getVarValue(), linkToProcess,
                        FlowPhasesEnum.P3_TRANSFORM_TO_ENTITY.getVarValue()).logDefToString());

                IEntityTransform fromSomethingToEntity = new TWTransformationFactory().getTransformation();
                fromSomethingToEntity.transform(githubObjects);

                // ----------------------- Log and execute mapping Entity to JSON -------------------------//
                log.info(ctx + ": " + new LogDef(LogTypeEnum.TYPE_EXECUTION.getVarValue(), linkToProcess,
                        FlowPhasesEnum.P4_MAP_ENTITY_TO_JSON.getVarValue()).logDefToString());

                // ----------------------- Add all generated entities to the final list of entities ---------------//
                GHJob.threatIntEntityList.addAll(fromSomethingToEntity.getThreatIntEntityList());

                log.info(ctx + ": " + new LogDef(LogTypeEnum.TYPE_EXECUTION.getVarValue(), linkToProcess,
                        FlowPhasesEnum.P5_END_FILE_PROCESS.getVarValue()).logDefToString());
            } catch (Exception jne) {
                log.error(ctx + ": " + new LogDef(LogTypeEnum.TYPE_ERROR.getVarValue(), linkToProcess,
                        jne.getLocalizedMessage()).logDefToString()
                );
            }
        }
    }
}
