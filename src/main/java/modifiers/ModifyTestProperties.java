package modifiers;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import util.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * Created by Ihar_Chekan on 10/14/2016.
 */
public class ModifyTestProperties {

    final static Logger logger = Logger.getLogger(ModifyTestProperties.class);

    private static ArrayList<String> allClusterNodes = new ArrayList<String>();

    public static void modifyAllTestProperties( String pathToTestProperties ) throws IOException {
        setSecuredValue( pathToTestProperties );
        setShimActive( pathToTestProperties );
        setSshSeverUserPassword( pathToTestProperties );
        setHdfsServerProtoPortUrl( pathToTestProperties );
        setJobTrackerServer( pathToTestProperties );
        setHiveHost( pathToTestProperties );
        setZookeeper( pathToTestProperties );
        setOozie( pathToTestProperties );
        setSpark( pathToTestProperties );
        setHdpVersion();
        setTextSplitter( pathToTestProperties );
        setSqoopSecureLibjarPath(pathToTestProperties);
        setHiveWarehouseDir( pathToTestProperties );
    }


    // set secured value
    private static void setSecuredValue( String pathToTestProperties ) {
        if ( ShimValues.isShimSecured() ) {
            PropertyHandler.setProperty( pathToTestProperties, "secure_cluster", "true" );
        } else {
            PropertyHandler.setProperty( pathToTestProperties, "secure_cluster", "false" );
        }
    }

    //set shim_active
    private static void setShimActive( String pathToTestProperties ) {
        PropertyHandler.setProperty( pathToTestProperties, "shim_active", ShimValues.getHadoopVendor()
                + ShimValues.getHadoopVendorVersion() );
    }

    // set sshServer, sshUser, sshPassword
    private static void setSshSeverUserPassword( String pathToTestProperties ) {
        PropertyHandler.setProperty( pathToTestProperties, "sshServer", ShimValues.getSshHost() );
        PropertyHandler.setProperty( pathToTestProperties, "sshUser", ShimValues.getSshUser() );
        PropertyHandler.setProperty( pathToTestProperties, "sshPassword", ShimValues.getSshPassword() );
    }

    // set hdfsServer, hdfsProto, hdfsPort, hdfsUrl values
    private static void setHdfsServerProtoPortUrl( String pathToTestProperties ) {
        String defaultFS = XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim() + "core-site.xml",
                "fs.defaultFS" );
        String[] defaultFsSplitForHdfsProto = defaultFS.split( "://" );
        String[] defaultFsSplitForHdfsPort = defaultFsSplitForHdfsProto[ 1 ].split( ":" );
        PropertyHandler.setProperty( pathToTestProperties, "hdfsProto", defaultFsSplitForHdfsProto[ 0 ] );
        PropertyHandler.setProperty( pathToTestProperties, "hdfsServer", defaultFsSplitForHdfsPort[ 0 ] );
        if ( defaultFsSplitForHdfsPort.length == 1 ) {
            PropertyHandler.setProperty( pathToTestProperties, "hdfsPort", "" );
            PropertyHandler.setProperty( pathToTestProperties, "hdfsUrl", "${hdfsProto}://${hdfsServer}" );
        } else {
            PropertyHandler.setProperty( pathToTestProperties, "hdfsPort", defaultFsSplitForHdfsPort[ 1 ] );
            PropertyHandler.setProperty( pathToTestProperties, "hdfsUrl", "${hdfsProto}://${hdfsServer}:${hdfsPort}" );
        }
    }

    //add jobTrackerServer / jobTrackerPort
    //for hdp we take it from yarn.resourcemanager.address property
    private static void setJobTrackerServer( String pathToTestProperties ) {
        if ( XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim() + "yarn-site.xml",
                "yarn.resourcemanager.address" ) != null ) {
            String[] rmAddress = XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim() + "yarn-site.xml",
                    "yarn.resourcemanager.address" ).split( ":" );
            PropertyHandler.setProperty( pathToTestProperties, "jobTrackerServer", rmAddress[ 0 ] );
            PropertyHandler.setProperty( pathToTestProperties, "jobTrackerPort", rmAddress[ 1 ] );
        } else {
            //for cdh we take it from yarn.resourcemanager.address.someAlias , aliases can be found in yarn.resourcemanager.ha.rm-ids
            String[] rmAlias = XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim() + "yarn-site.xml",
                    "yarn.resourcemanager.ha.rm-ids" ).split( "[,]" );
            String[] rmAddress = XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim() + "yarn-site.xml",
                    "yarn.resourcemanager.address" + "." + rmAlias[ 0 ] ).split( ":" );
            PropertyHandler.setProperty( pathToTestProperties, "jobTrackerServer", rmAddress[ 0 ] );
            PropertyHandler.setProperty( pathToTestProperties, "jobTrackerPort", rmAddress[ 1 ] );
        }
    }

    // determine hive host and set all values for it
    //TODO: Refactor this and other methods - need to create interface(abstract class?) for different hadoop vendors
    private static void setHiveHost( String pathToTestProperties ) {
        if ( ShimValues.getHadoopVendor().equalsIgnoreCase( "cdh" ) ) {
            String allClusterNodesFromRest = new String( RestClient.callRest( "http://" + ShimValues.getRestHost() + ":7180/api/v10/hosts",
                    RestClient.HttpMethod.HTTP_METHOD_GET,
                    ShimValues.getRestUser(), ShimValues.getRestPassword(), null, null, null ) );
            try {
                JSONObject obj = new JSONObject( allClusterNodesFromRest );
                JSONArray arr = obj.getJSONArray( "items" );
                for ( int i = 0; i < arr.length(); i++ ) {
                    JSONObject obj2 = arr.getJSONObject( i );
                    String hostname = obj2.getString( "hostname" );
                    allClusterNodes.add( hostname );
                }
            } catch ( JSONException e ) {
                logger.error( "JSON exception: " + e );
            }
        } else {
            String allClusterNodesFromRest = new String( RestClient.callRest( "http://" + ShimValues.getRestHost() + ":8080/api/v1/hosts",
                    RestClient.HttpMethod.HTTP_METHOD_GET,
                    ShimValues.getRestUser(), ShimValues.getRestPassword(), null, null, null ) );
            try {
                JSONObject obj = new JSONObject( allClusterNodesFromRest );
                JSONArray arr = obj.getJSONArray( "items" );
                for ( int i = 0; i < arr.length(); i++ ) {
                    JSONObject obj2 = arr.getJSONObject( i );
                    JSONObject obj3 = obj2.getJSONObject( "Hosts" );
                    String hostname = obj3.getString( "host_name" );
                    allClusterNodes.add( hostname );
                }
            } catch ( JSONException e ) {
                logger.error( "JSON exception: " + e );
            }
        }
        String hiveServerNode = "";
        for ( String node : allClusterNodes ) {
            if ( SSHUtils.getCommandResponseBySSH( ShimValues.getSshUser(), node, ShimValues.getSshPassword(),
                    "ps aux | grep HiveServer2" ).contains( "org.apache.hive.service.server.HiveServer2" ) ) {
                hiveServerNode = node;
            }
        }
        if ( !hiveServerNode.equals( "" ) ) {
            PropertyHandler.setProperty( pathToTestProperties, "hive2_hostname", hiveServerNode );
            //If vendor is cdh - adding Impala properties, same as for hive
            if ( ShimValues.getHadoopVendor().equalsIgnoreCase( "cdh" ) ) {
                PropertyHandler.setProperty( pathToTestProperties, "impala_hostname", hiveServerNode );
            }
        } else {
            logger.error( "Hive node was not determined!!!" );
        }
        //if secured - add hive principal
        if ( ShimValues.isShimSecured() ) {
            if ( ShimValues.getHadoopVendor().equalsIgnoreCase( "cdh" ) ) {
                String cmCluster = new String( RestClient.callRest( "http://" + ShimValues.getRestHost() + ":7180/api/v10/clusters",
                        RestClient.HttpMethod.HTTP_METHOD_GET,
                        ShimValues.getRestUser(), ShimValues.getRestPassword(), null, null, null ) );
                String cluster = "";
                try {
                    JSONObject obj = new JSONObject( cmCluster );
                    cluster = obj.getJSONArray( "items" ).getJSONObject( 0 ).getString( "name" );
                } catch ( JSONException e ) {
                    logger.error( "JSON exception: " + e );
                }

                byte[] zipFromCM = RestClient.callRest( "http://" + ShimValues.getRestHost()
                                + ":7180/api/v10/clusters/" + cluster + "/services/hive/clientConfig",
                        RestClient.HttpMethod.HTTP_METHOD_GET,
                        ShimValues.getRestUser(), ShimValues.getRestPassword(), null, null, null );

                File tempHiveSiteXML = null;
                try {
                    tempHiveSiteXML = ShimFileUtils.getFileFromZipAndSaveAsTempFile( zipFromCM, "hive-site.xml" );
                } catch ( IOException e ) {
                    logger.error ("IOException on hive: " + e);
                }

                String[] hivePrincipalTemp1 = XmlPropertyHandler.readXmlPropertyValue( tempHiveSiteXML.getAbsolutePath(),
                        "hive.metastore.kerberos.principal" ).split( "/" );

                String[] hivePrincipalTemp2 = hivePrincipalTemp1[ 1 ].split( "@" );
                String hivePrincipal = hivePrincipalTemp1[ 0 ] + "/" + hiveServerNode + "@" + hivePrincipalTemp2[ 1 ];
                String fullImpalaConfig =
                        new String( RestClient.callRest( "http://" + ShimValues.getRestHost() + ":7180/api/v10/clusters/" + cluster
                                        + "/services/impala/config?view=FULL", RestClient.HttpMethod.HTTP_METHOD_GET,
                              ShimValues.getRestUser(), ShimValues.getRestPassword(), null, null,
                                null ) );

                String impalaKrbServiceName = "";
                try {
                    JSONObject obj = new JSONObject( fullImpalaConfig );
                    JSONArray arr = obj.getJSONArray( "items" );
                    for ( int i = 0; i < arr.length(); i++ ) {
                        if ( arr.getJSONObject( i ).getString( "name" ).equalsIgnoreCase( "kerberos_princ_name" ) ) {
                            JSONObject obj2 = arr.getJSONObject( i );
                            impalaKrbServiceName = obj2.getString( "default" );
                            break;
                        }
                    }
                } catch ( JSONException e ) {
                    logger.error( "JSON exception: " + e );
                }

                PropertyHandler.setProperty( pathToTestProperties, "hive2_option", "principal" );
                PropertyHandler.setProperty( pathToTestProperties, "hive2_principal", hivePrincipal );
                PropertyHandler.setProperty( pathToTestProperties, "impala_KrbRealm", hivePrincipalTemp2[ 1 ] );
                PropertyHandler.setProperty( pathToTestProperties, "impala_KrbHostFQDN", hiveServerNode );
                PropertyHandler.setProperty( pathToTestProperties, "impala_KrbServiceName", impalaKrbServiceName );
            } else {
                String ambariCluster = new String( RestClient.callRest( "http://" + ShimValues.getRestHost() + ":8080/api/v1/clusters/",
                        RestClient.HttpMethod.HTTP_METHOD_GET,
                        ShimValues.getRestUser(), ShimValues.getRestPassword(), null, null, null ) );
                String cluster = "";
                try {
                    JSONObject obj = new JSONObject( ambariCluster );
                    cluster =
                            obj.getJSONArray( "items" ).getJSONObject( 0 ).getJSONObject( "Clusters" ).getString( "cluster_name" );
                } catch ( JSONException e ) {
                    logger.error( "JSON exception: " + e );
                }

                String ambariHive = new String( RestClient.callRest( "http://" + ShimValues.getRestHost() + ":8080/api/v1/clusters/"
                                + cluster + "/configurations/service_config_versions?service_name.in(HIVE)&is_current=true",
                        RestClient.HttpMethod.HTTP_METHOD_GET,
                        ShimValues.getRestUser(), ShimValues.getRestPassword(), null, null, null ) );

                String ambariPrincipal = "";
                try {
                    JSONObject obj = new JSONObject( ambariHive );
                    JSONArray arr = obj.getJSONArray( "items" ).getJSONObject( 0 ).getJSONArray( "configurations" );
                    for ( int i = 0; i < arr.length(); i++ ) {
                        if ( arr.getJSONObject( i ).getString( "type" ).equalsIgnoreCase( "hive-site" ) ) {
                            JSONObject obj2 = arr.getJSONObject( i ).getJSONObject( "properties" );
                            ambariPrincipal = obj2.getString( "hive.metastore.kerberos.principal" );
                            break;
                        }
                    }
                } catch ( JSONException e ) {
                    logger.error( "JSON exception: " + e );
                }

                String[] hivePrincipalTemp1 = ambariPrincipal.split( "/" );
                String[] hivePrincipalTemp2 = hivePrincipalTemp1[ 1 ].split( "@" );
                String hivePrincipal = hivePrincipalTemp1[ 0 ] + "/" + hiveServerNode + "@" + hivePrincipalTemp2[ 1 ];

                PropertyHandler.setProperty( pathToTestProperties, "hive2_option", "principal" );
                PropertyHandler.setProperty( pathToTestProperties, "hive2_principal", hivePrincipal );

            }
        } else {
            PropertyHandler.setProperty( pathToTestProperties, "hive2_option", "" );
            PropertyHandler.setProperty( pathToTestProperties, "hive2_principal", "" );
            PropertyHandler.setProperty( pathToTestProperties, "impala_KrbRealm", "" );
            PropertyHandler.setProperty( pathToTestProperties, "impala_KrbHostFQDN", "" );
            PropertyHandler.setProperty( pathToTestProperties, "impala_KrbServiceName", "" );
        }
    }

    // add zookeeper host and port
    //for hdp it can be taken from "hadoop.registry.zk.quorum" property
    private static void setZookeeper( String pathToTestProperties ) {
        String zkQuorum = "";
        String zkQuorumRes = "";
        String zkPort = "";

        if ( XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim() + "yarn-site.xml",
                "hadoop.registry.zk.quorum" ) != null ) {
            zkQuorum = XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim() + "yarn-site.xml",
                    "hadoop.registry.zk.quorum" );
            //for cdh it can be taken from "yarn.resourcemanager.zk-address" property
        } else if ( XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim() + "yarn-site.xml",
                "yarn.resourcemanager.zk-address" ) != null ) {
            zkQuorum = XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim() + "yarn-site.xml",
                    "yarn.resourcemanager.zk-address" );
        }
        // parsing zookeeper address if we have it
        if ( !zkQuorum.equalsIgnoreCase( "" ) ) {
            String[] zkQuorumTemp1 = zkQuorum.split( "[,]" );
            ArrayList<String> zkQuorumArrayRes = new ArrayList<>();
            for ( int i = 0; i < zkQuorumTemp1.length; i++ ) {
                String[] zTemp = zkQuorumTemp1[ i ].split( ":" );
                zkQuorumArrayRes.add( zTemp[ 0 ] );
                zkPort = zTemp[ 1 ];
            }
            zkQuorumRes = String.join( ",", zkQuorumArrayRes );
        }

        if ( zkQuorum.equalsIgnoreCase( "" ) && XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim()
                + "hbase-site.xml", "hbase.zookeeper.quorum" ) != null ) {
            zkQuorumRes = XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim() + "hbase-site.xml",
                    "hbase.zookeeper.quorum" );
            zkPort = XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim() + "hbase-site.xml",
                    "hbase.zookeeper.property.clientPort" );
        }

        if ( zkQuorumRes.equalsIgnoreCase( "" ) || zkPort.equalsIgnoreCase( "" ) ) {
            logger.error( "Both \"hadoop.registry.zk.quorum\" or \"hadoop.registry.zk.quorum\" properties "
                    + "was not found in \"yarn-site.xml\" and \"hbase.zookeeper.quorum\" was not helpful as well... " );
        }

        // actual adding zookeeper host and port
        PropertyHandler.setProperty( pathToTestProperties, "zookeeper_host", zkQuorumRes );
        PropertyHandler.setProperty( pathToTestProperties, "zookeeper_port", zkPort );
    }

    // Adding Oozie oozie_server
    private static void setOozie( String pathToTestProperties ) {
        for ( String node : allClusterNodes ) {
            if ( SSHUtils.getCommandResponseBySSH( ShimValues.getSshUser(), node, ShimValues.getSshPassword(),
                    "ps aux | grep oozie" ).contains( " -Doozie.http.hostname" ) ) {
                PropertyHandler.setProperty( pathToTestProperties, "oozie_server", node );
            }
        }
    }
    //find and set spark-assembly jar
    private static void setSpark( String pathToTestProperties ) {
        // if it is hdp cluster - 2 more properties are needed
        if ( ShimValues.getHadoopVendor().equalsIgnoreCase( "hdp" ) ) {
            String hdpVersion = SSHUtils.getCommandResponseBySSH(
                    ShimValues.getSshUser(), ShimValues.getSshHost(), ShimValues.getSshPassword(),
                    "hdp-select versions" ).replaceAll( "\\r|\\n", "" );
            PropertyHandler
                    .setProperty( pathToTestProperties, "spark_driver_extraJavaOptions", "-Dhdp.version=" + hdpVersion );
            PropertyHandler
                    .setProperty( pathToTestProperties, "spark_yarn_am_extraJavaOptions", "-Dhdp.version=" + hdpVersion );
        } else {
            PropertyHandler
                    .setProperty( pathToTestProperties, "spark_driver_extraJavaOptions", "" );
            PropertyHandler
                    .setProperty( pathToTestProperties, "spark_yarn_am_extraJavaOptions", "" );
        }
    }

    // TODO: determine if this is really needed
    private static void setHdpVersion() {
        String configPropertiesFile = ShimValues.getPathToShim() + File.separator + "config.properties";
        if ( ShimValues.getHadoopVendor().equalsIgnoreCase( "hdp" ) ) {
            String hdpVersion = SSHUtils.getCommandResponseBySSH(
                    ShimValues.getSshUser(), ShimValues.getSshHost(), ShimValues.getSshPassword(),
                    "hdp-select versions" ).replaceAll( "\\r|\\n", "" );
            PropertyHandler.setProperty( configPropertiesFile, "java.system.hdp.version", hdpVersion );
        }
    }

    //modifying allow_text_splitter value
    private static void setTextSplitter( String pathToTestProperties ) {
        if ( ShimValues.getHadoopVendor().equalsIgnoreCase( "hdp" )
                && Integer.valueOf( ShimValues.getHadoopVendorVersion() ) > 24 ) {
            PropertyHandler.setProperty( pathToTestProperties, "allow_text_splitter", "org.apache.sqoop.splitter.allow_text_splitter" );
            PropertyHandler.setProperty( pathToTestProperties, "allow_text_splitter_value", "true" );
        }
        else {
            PropertyHandler.setProperty( pathToTestProperties, "allow_text_splitter", "" );
            PropertyHandler.setProperty( pathToTestProperties, "allow_text_splitter_value", "" );
        }
    }

    //set sqoop_secure_libjar_path
    private static void setSqoopSecureLibjarPath ( String pathToTestProperties ) throws IOException {
        if (ShimValues.isShimSecured() ) {
            try {
                // todo: add try/catch to check if this file exists
                String filename = Files.find( Paths.get( ShimValues.getPathToShim() + File.separator + "lib" ), 1,
                  ( p, bfa ) -> bfa.isRegularFile() && p.getFileName().toString()
                  .matches( "pentaho-hadoop-shims-.+?-security-.+?\\.jar" ) ).findFirst()
                  .get().toAbsolutePath().normalize().toUri().toString();

                PropertyHandler.setProperty(pathToTestProperties, "sqoop_secure_libjar_path", filename);
            } catch ( FileNotFoundException fnfe ) {
                logger.error( fnfe );
            }
        } else {
            PropertyHandler.setProperty(pathToTestProperties, "sqoop_secure_libjar_path", "");
        }
    }

    private static void setHiveWarehouseDir( String pathToTestProperties ) {
        String hiveWarehouse = XmlPropertyHandler.readXmlPropertyValue( ShimValues.getPathToShim() + "hive-site.xml",
          "hive.metastore.warehouse.dir" );
        PropertyHandler.setProperty( pathToTestProperties, "hive_warehouse", hiveWarehouse );
    }

}
