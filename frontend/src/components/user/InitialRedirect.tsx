import React, { useEffect, useState } from 'react';
// import { Redirect } from 'react-router-dom';
// import { mapStateToProps, mapDispatchToProps, mergeProps, initialRefresh } from 'store';
import { connect } from 'react-redux';
import { Loading } from 'components/shared/Loading';

// function InitialRedirectComponentBase() {
//   const [loading, setLoading] = useState();
//   const [completed, setCompleted] = useState();

//   useEffect(() => {

//   })
// }


class InitialRedirectComponentBase extends React.Component {
  state = {
    loading: true,
    completed: undefined,
  }

  // checkNux = () => {
  //   this.props.curl({
  //     method: 'GET',
  //     url: '/api/orgs/-1/nux'
  //   }).then((resp) => {
  //     this.setState({
  //       loading: false,
  //       completed: resp.data.completed
  //     });
  //   }).catch((e) => {
  //     this.setState({
  //       error: e.message,
  //       loading: false,
  //     });
  //     return Promise.reject(e);
  //   });
  // }

  // componentDidMount() {
  //   this.checkNux();
  // }

  render() {
    return <div>Error</div>
    // if(this.state.loading) {
    //   return <Loading loading={true} />
    // } else if (this.state.completed) {
    //   return <Redirect to="/console"/>
    // } else if (this.state.completed === false){
    //   return <Redirect to="/onboarding"/>
    // } else {
    //   return <div>Error</div>
    // }
  }
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
