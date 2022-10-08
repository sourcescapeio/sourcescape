import React, { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
// import { mapStateToProps, mapDispatchToProps, mergeProps, initialRefresh } from 'store';
import { connect } from 'react-redux';
import { Loading } from 'components/shared/Loading';
import axios from 'axios';

function InitialRedirectComponentBase() {
  const [loading, setLoading] = useState(true);
  const [completed, setCompleted] = useState<boolean | null>(null);
  const [error, setError] = useState<string | null>(null);

  return <Navigate to="/srclog-console" />  

  // to be replaced by graphql
  // useEffect(() => {
  //   axios({
  //     method: 'GET',
  //     url: '/api/orgs/-1/nux'
  //   }).then((resp) => {
  //     setLoading(false);
  //     setCompleted(resp.data.completed);
  //   }).catch((e) => {
  //     setLoading(false);
  //     setError(e.message);
  //     return Promise.reject(e);
  //   });
  // }, []);

  // if(loading) {
  //   return <Loading loading={true} />
  // } else if (completed) {

  // } else if (completed === false) {
  //   return <Navigate to="/onboarding" />
  // } else {
  //   return <div>{error}</div>
  // }
}

// const InitialRedirectComponent = connect(
//   mapStateToProps,
//   mapDispatchToProps,
//   mergeProps,
// )(InitialRedirectComponentBase);
const InitialRedirectComponent = InitialRedirectComponentBase;

export {
  InitialRedirectComponent,
}
