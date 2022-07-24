import { NonIdealState, Dialog, ProgressBar, MultistepDialog, DialogStep, Classes, H3, FormGroup, HTMLTable, InputGroup, Button } from "@blueprintjs/core";
import axios from "axios";
import { Loading } from "components/shared/Loading";
import { useFormik } from "formik";
import { useEffect, useState } from "react";
import { Col, Row } from "react-bootstrap";
import Container from "react-bootstrap/Container";
import { Navigate } from "react-router";


function OnboardingScanAddDirectoryComponent() {
  const formik = useFormik({
    initialValues: {
      directory: ''
    },
    onSubmit: values => {
      alert(JSON.stringify(values, null, 2))
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
  return <div>
    <Container>
      <H3>Scanning...</H3>
      <OnboardingScanAddDirectoryComponent />
    </Container>
  </div>
}

// all data lives outside?
function OnboardingComponent(props: {}) {
  // const { progress } = (props.queue['scan'] || {})
  const progress = 100;
  return <MultistepDialog    
    isOpen={true}
    navigationPosition={"top"}
    nextButtonProps={{
      disabled: false,
            
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
      panel={<div className={Classes.DIALOG_BODY}>Scan</div>}
      title="Index"
    />
    <DialogStep
      id="confirm"
      panel={<div className={Classes.DIALOG_BODY}>Scan</div>}
      title="Confirm"
    />
  </MultistepDialog>
  // return <Dialog isOpen={true}>
  //   <NonIdealState
  //     icon="barcode"
  //     title="Scanning"
  //     description={
  //       <div>
  //         <ProgressBar intent={progress ? "primary" : "none"} value={progress && (progress / 100.0)}/>
  //         Scanning for repos
  //         <pre>sourcescape up [your repo]</pre>
  //       </div>
  //     }
  //   />
  // </Dialog>
}

export function LocalOnboardingContainer() {

  const [loading, setLoading] = useState(true);
  const [repos, setRepos] = useState([]);
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

  // this should be all in redux
  useEffect(() => {
    axios({
      method: 'GET',
      url: '/api/orgs/-1/repos'
    }).then((resp) => {
      console.warn(resp.data);
      setLoading(false);
      setRepos(resp.data)
    })
  }, []);

  if(loading) {
    return <Loading loading={true} />
  }

  if(complete) {
    return <Navigate to="/console" />
  }

  return <OnboardingComponent />;

  // if (repos.length === 0) {
  //   return <div>Scanning...</div>
  // }

  // return <div>
  //   Indexing...
  //   <pre>{JSON.stringify(repos, null, 2)}</pre>
  // </div>
}
