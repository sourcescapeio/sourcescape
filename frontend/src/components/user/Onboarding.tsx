import { NonIdealState, Dialog, ProgressBar, MultistepDialog, DialogStep, Classes, H3, FormGroup, HTMLTable, InputGroup, Button, Spinner, Text } from "@blueprintjs/core";
import axios from "axios";
import { Loading } from "components/shared/Loading";
import { useFormik } from "formik";
import { useEffect, useState } from "react";
import { Col, Row } from "react-bootstrap";
import Container from "react-bootstrap/Container";
import { Navigate } from "react-router";
import { gql, useMutation, useQuery, useSubscription } from '@apollo/client';


type RepoIndex = {
  id: number
  cloneProgress: number
  indexProgress: number
}

const SCANS_QUERY = gql`
  query GetScans {
    scans {
      id
      path
      progress
    }
  }
`;

const SCAN_DIRECTORY = gql`
  mutation ScanDirectory($path: String!) {
    createScan(path: $path) {
      id
      path
    }
  }
`;

const SCAN_DELETE = gql`
  mutation ScanDelete($id: Int!) {
    deleteScan(id: $id) {
      id
    }
  }
`

const SCAN_PROGRESS_SUBSCRIPTION = gql`
  subscription ScanProgress {
    scanProgress {
      id
      progress
    }
  }
`

const CLONING_PROGRESS_SUBSCRIPTION = gql`
  subscription CloningProgress {
    cloneProgress {
      indexId
      repoId
      progress
    }
  }
`

const INDEX_PROGRESS_SUBSCRIPTION = gql`
  subscription IndexingProgress {
    indexProgress {
      indexId
      repoId
      progress
    }
  }
`

const SELECT_REPO = gql`
  mutation SelectRepo($id: Int!) {
    selectRepo(id: $id)
  }
`

// we do want a progress
const REPOS_QUERY = gql`
  query GetRepos {
    repos {
      id
      path
      name
      intent
      indexes {
        id
        cloneProgress
        indexProgress
      }
    }
  }
`

function OnboardingScanAddDirectoryComponent() {
  const [scanDirectory, { data, loading, error} ] = useMutation(SCAN_DIRECTORY, {
    refetchQueries: [
      { query: SCANS_QUERY },
      { query: REPOS_QUERY }
    ]
  });

  const formik = useFormik({
    initialValues: {
      directory: ''
    },
    onSubmit: (values, { resetForm }) => {
      resetForm();
      scanDirectory({variables: { path: values.directory}})
    }
  });

  return <form onSubmit={formik.handleSubmit}>
    <Row>
      <Col>
        <InputGroup 
          onChange={formik.handleChange} 
          name="directory"
          placeholder="Add directory"
          value={formik.values.directory}
        />
      </Col>
      <Col>
        <Button icon="add" intent="success" minimal={true} type="submit" />
      </Col>
    </Row>    
  </form>
}


function OnboardingScanComponent() {
  const { subscribeToMore, loading, error, data } = useQuery(SCANS_QUERY);
  const [deleteScan, deleteStatus] = useMutation(SCAN_DELETE, {
    refetchQueries: [
      { query: SCANS_QUERY },
      { query: REPOS_QUERY }
    ]
  });

  // mount
  useEffect(() => {
    subscribeToMore({
      document: SCAN_PROGRESS_SUBSCRIPTION,
      updateQuery: (prev, { subscriptionData }) => {
        const { scans } = prev;
        const { progress, id } = subscriptionData.data.scanProgress;
        const newScans = (scans || []).map((item: any) => {
          if (item.id.toString() === id) {
            return {
              ...item,
              progress: progress
            }
          } else {
            return item;
          }
        });

        return { scans: newScans };
      }
    });
  }, []);

  if (loading || deleteStatus.loading) {
    return <Loading loading={true} />
  }

  // error
  if (error || !Array.isArray(data.scans)) {
    return <div>Error</div>
  }

  return <div>
    <Container>
      <H3>Scanning...</H3>
      {/* <pre>{JSON.stringify(subscriptionResult.data)}</pre> */}
      {deleteStatus.error && <pre>{JSON.stringify(deleteStatus.error, null, 2)}</pre>}
      {
        data.scans.map((d: any) => {
          return <Row key={d.id}>
          <Col>
            {d.path}
          </Col>
          <Col>
            {d.progress === 100 ? 
              <Button icon="trash" intent="danger" minimal={true} onClick={() => {
                deleteScan({variables: {id: d.id}})
              }}/> : <Spinner size={20} value={d.progress / 100.0}/>
            }
          </Col>
        </Row>
        })
      }
      <OnboardingScanAddDirectoryComponent />
      {/* <pre>{JSON.stringify(data.scans, null, 2)}</pre> */}
    </Container>
  </div>
}


function OnboardingIndexingProgress(props: {indexes: RepoIndex[]}) {
  const { indexes } = props;
  if(indexes.length === 0) {
    return <div>
      <Spinner size={20}/>
      Standby
    </div>
  } else {
    const bestIndex = indexes[0]; // shurg
    const { cloneProgress, indexProgress } =  indexes[0];

    if (cloneProgress === 100 && indexProgress === 100) {
      return <div>Indexed</div>
    }
    if (cloneProgress === 100) {
      return <div>
        <Spinner size={20} value={indexProgress / 100.0}/>
        Indexing
      </div>
    } else {
      return <div>
        <Spinner size={20} value={cloneProgress / 100.0}/>
        Cloning
      </div>
    }
  }
}


function updateRepos(repos: { id: number, indexes: RepoIndex[]}[], key: string, payload: {indexId: string, repoId: number, progress: number}) {
  return repos.map((item) => {
    if(item.id === payload.repoId) {
      var contains: boolean = false;
      const newIndexes = item.indexes.map((i: any) => {
        if (i.id === payload.indexId) {
          contains = true;
          return {
            ...i,
            [key]: payload.progress,
          }
        } else {
          return i;
        }
      });

      return {
        ...item,
        indexes: contains ? newIndexes : [...newIndexes, {id: payload.indexId, [key]: payload.progress}]
      }
    } else {
      return item;
    }
  });
}

function OnboardingIndexingComponent(props: any) {
  const { subscribeToMore, loading, data, error } = useQuery(REPOS_QUERY);
  const [selectRepo, selectStatus] = useMutation(SELECT_REPO, {
    refetchQueries: [
      { query: REPOS_QUERY }
    ]
  });

  const [filter, setFilter] = useState('');

  useEffect(() => {
    subscribeToMore({
      document: INDEX_PROGRESS_SUBSCRIPTION,
      updateQuery: (prev, { subscriptionData }) => {
        console.warn(prev, subscriptionData)
        // NASTY NASTY
        const { repos } = prev;
        const newRepos = updateRepos(repos, 'indexProgress', subscriptionData.data.indexProgress);

        return { repos: newRepos };
      }
    });

    subscribeToMore({
      document: CLONING_PROGRESS_SUBSCRIPTION,
      updateQuery: (prev, { subscriptionData }) => {
        // NASTY NASTY
        const { repos } = prev;
        const newRepos = updateRepos(repos, 'cloneProgress', subscriptionData.data.cloneProgress);

        return { repos: newRepos };
      }
    });    
  }, []);


  if (!data) {
    return <div></div>
  }  

  const filteredRepos = data.repos.filter((r: any) => (r.name.toLowerCase().indexOf(filter) > -1));
  const rows = filteredRepos.map((r: any) => {
    return <tr key={r.id}>
      <td>
        <div style={{width: 350}}>
          <div><Text ellipsize={true}><b>{r.name}</b></Text></div>
          <div style={{fontSize: 12}}><Text ellipsize={true}>{' '}[{r.path}]</Text></div>
        </div>
      </td>
      <td>
        { (r.intent === "Collect") ?
          <OnboardingIndexingProgress indexes={r.indexes} /> : <Button 
            minimal
            icon="cloud-download"
            onClick={() => {
              selectRepo({variables: {id: r.id}})
            }}
            intent={"success"}
          />
        }

      </td>
    </tr>;
  });

  return <div
    style={{maxHeight: 400, padding: 10, overflowY: 'scroll'}}
  >
    <InputGroup
      value={filter}
      onChange={(e) => {
        setFilter(e.target.value)
      }}
      placeholder="Filter repos..."
      rightElement={<Button icon="cross" minimal onClick={ () => {
        setFilter('')
      }}/>}
    />
    <HTMLTable style={{contentVisibility: 'auto'}}>
      {rows}
    </HTMLTable>
    {/* <pre>{JSON.stringify(data, null, 2)}</pre> */}
  </div>
}

// all data lives outside?
function OnboardingComponent(props: {completeNux: () => Promise<void>}) {
  // const { progress } = (props.queue['scan'] || {})
  const progress = 100;
  return <MultistepDialog    
    isOpen={true}
    navigationPosition={"top"}
    nextButtonProps={{
      disabled: false,
    }}
    finalButtonProps={{
      onClick: () => {
        props.completeNux()
      },
      text: 'Finish'
    }}
  >
    <DialogStep
      id="scan"
      panel={<div className={Classes.DIALOG_BODY}>
        <OnboardingScanComponent />
      </div>}
      title="Scan"
    />
    <DialogStep
      id="index"
      panel={<div className={Classes.DIALOG_BODY}>
        <OnboardingIndexingComponent />
      </div>}
      title="Index"
    />
  </MultistepDialog>
}

export function LocalOnboardingContainer() {

  const [complete, setComplete] = useState(false);

  const completeNux = () => {
    return axios({
      method: 'PUT',
      url: `/api/orgs/-1/nux`,
    }).then(() => {
      setComplete(true);
    }).catch((e) => {
      // this.setState({
      //   error: e.message,
      //   loading: false,
      // });
      return Promise.reject(e);
    });
  }

  if(complete) {
    return <Navigate to="/srclog-console" />
  }

  return <OnboardingComponent completeNux={completeNux}/>;
}
